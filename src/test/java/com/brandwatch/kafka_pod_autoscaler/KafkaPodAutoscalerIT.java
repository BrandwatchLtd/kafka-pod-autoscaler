package com.brandwatch.kafka_pod_autoscaler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.DockerDaemonImage;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.RegistryImage;

import brandwatch.com.v1alpha1.KafkaPodAutoscaler;
import brandwatch.com.v1alpha1.KafkaPodAutoscalerSpec;
import brandwatch.com.v1alpha1.kafkapodautoscalerspec.ScaleTargetRef;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.testing.FileConsumer;

@Slf4j
@Testcontainers
class KafkaPodAutoscalerIT {
    private static final String OPERATOR_NAMESPACE = "system-kpa";
    private static final String IMAGE_NAME = System.getProperty("imageName");
    public static final int POD_STARTUP_TIMEOUT = 60;
    private static final int REGISTRY_PORT = 5000;
    private static final Map<String, Map<String, LogWatch>> watchLogs = new ConcurrentHashMap<>();

    @Container
    public static K3sContainer k3s = new K3sContainer(DockerImageName.parse("rancher/k3s:v1.23.17-k3s1"))
            .withNetwork(Network.newNetwork())
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("/k3s-registries.yaml"),
                    "/etc/rancher/k3s/registries.yaml"
            )
            .withLogConsumer(new FileConsumer(Path.of("target/k3s.log")));
    @Container
    public static final GenericContainer<?> registryContainer = new GenericContainer<>(DockerImageName.parse("registry:2.7.1"))
            .withEnv("REGISTRY_HTTP_SECRET", "secret")
            .withNetwork(k3s.getNetwork())
            .withNetworkAliases("registry")
            .withExposedPorts(REGISTRY_PORT)
            .withPrivilegedMode(true)
            .waitingFor(Wait.forHttp("/v2/_catalog").forStatusCode(200))
            .withLogConsumer(new FileConsumer(Path.of("target/registry.log")));
    @Container
    public static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
            .withNetwork(k3s.getNetwork())
            .withNetworkAliases("kafka");

    private String namespace;
    private static KubernetesClient client;

    @BeforeAll
    static void beforeAll() throws Exception {
        // Use jib to upload the image to the temp registry
        Jib.from(DockerDaemonImage.named(IMAGE_NAME))
           .containerize(
                   Containerizer.to(RegistryImage.named("localhost:" + registryContainer.getMappedPort(REGISTRY_PORT) + "/kafka-pod-autoscaler"))
                                .setAllowInsecureRegistries(true)
           );

        // Use Config.fromKubeconfig instead of passing the raw string to withConfig,
        // as withConfig uses your local kubeconfig file whether you like it or not
        var config = Config.fromKubeconfig(k3s.getKubeConfigYaml());
        client = new KubernetesClientBuilder().withConfig(config).build();

        client.namespaces()
              .resource(new NamespaceBuilder().withNewMetadata().withName(OPERATOR_NAMESPACE)
                                              .endMetadata().build())
              .create();

        logger.info("Deploying operator");
        deployOperator();
    }

    @BeforeEach
    void setup() {
        namespace = "kpa-it-" + UUID.randomUUID();

        client.namespaces()
              .resource(new NamespaceBuilder().withNewMetadata().withName(namespace)
                                              .endMetadata().build())
              .create();
    }

    @AfterEach
    void tearDown() {
        if (client == null) {
            // setup failed
            return;
        }
        if (watchLogs.containsKey(namespace)) {
            // Cancel any watches for pods in this namespace
            for (var logWatch : watchLogs.get(namespace).values()) {
                logWatch.close();
            }
        }
        // Clean up the namespace
        client.namespaces().resource(new NamespaceBuilder().withNewMetadata().withName(namespace)
                                                           .endMetadata().build()).delete();
        await()
                .atMost(Duration.ofSeconds(60))
                .untilAsserted(() -> assertThat(client.namespaces().withName(namespace).get()).isNull());
    }

    @Test
    @Order(1)
    public void canCreateAutoscaler() {
        logger.info("Applying custom resource");
        var res = new KafkaPodAutoscaler();
        res.setMetadata(new ObjectMetaBuilder()
                                .withName("test")
                                .withNamespace(namespace)
                                .build());
        var ref = new ScaleTargetRef();
        ref.setName("does-not-exist");
        var spec = new KafkaPodAutoscalerSpec();
        spec.setScaleTargetRef(ref);
        spec.setTriggers(List.of());
        res.setSpec(spec);

        client.resource(res).create();
        assertAutoscalerStatus("test", "Deployment not found. Skipping scale");
    }

    @Test
    @Order(2)
    public void canScaleStatically() throws IOException {
        logger.info("Deploying test workload");
        applyKustomize("src/test/resources/workload/static");

        waitForPodsWithLabel("app", "statically-scaled", 1);

        applyKustomize("src/test/resources/autoscaler/static");

        waitForPodsWithLabel("app", "statically-scaled", 2);
        assertAutoscalerStatus("static-autoscaler", "Deployment being scaled from 1 to 2 replicas");
    }

    private void waitForPodsWithLabel(String key, String value, int expected) {
        waitForPodsWithLabel(namespace, key, value, expected);
    }

    private static void waitForPodsWithLabel(String namespace, String key, String value, int expected) {
        await().atMost(Duration.ofSeconds(POD_STARTUP_TIMEOUT)).untilAsserted(() -> {
            var pods = client.pods().inNamespace(namespace).withLabel(key, value).list().getItems();
            assertThat(pods.size()).isEqualTo(expected);

            var containerStatuses = pods.stream().flatMap(pod -> pod.getStatus().getContainerStatuses().stream()).toList();
            assertThat(containerStatuses.size()).isEqualTo(expected);
            assertThat(containerStatuses.stream().allMatch(ContainerStatus::getReady)).isTrue();

            for (Pod item : pods) {
                var podName = item.getMetadata().getName();
                watchLogs.computeIfAbsent(namespace, n -> new HashMap<>())
                         .computeIfAbsent(podName, p -> client.pods().inNamespace(namespace)
                                                              .withName(p)
                                                              .watchLog(System.out));
            }
        });
    }

    private void assertAutoscalerStatus(String name, String expectedStatus) {
        await().atMost(Duration.ofSeconds(POD_STARTUP_TIMEOUT)).untilAsserted(() -> {
            var autoscaler = client.resources(KafkaPodAutoscaler.class).inNamespace(namespace).withName(name).get();

            assertThat(autoscaler.getStatus()).isNotNull();
            assertThat(autoscaler.getStatus().getMessage()).isEqualTo(expectedStatus);
        });
    }

    private static void deployOperator() throws IOException {
        applyKustomize("src/test/resources/operator", OPERATOR_NAMESPACE);

        waitForPodsWithLabel(OPERATOR_NAMESPACE, "app", "kafka-pod-autoscaler", 1);
    }

    void applyKustomize(String path) throws IOException {
        applyKustomize(path, namespace);
    }

    static void applyKustomize(String path, String namespace) throws IOException {
        var process = Runtime.getRuntime().exec("kustomize build " + path);

        try (var yamlStream = process.getInputStream()) {
            List<HasMetadata> resources = client.load(yamlStream).items();
            resources.forEach(hm -> {
                hm.getMetadata().setNamespace(namespace);
                if (hm.getKind().toLowerCase(Locale.ROOT).equals("clusterrolebinding")) {
                    var crb = (ClusterRoleBinding) hm;
                    for (var subject : crb.getSubjects()) {
                        subject.setNamespace(namespace);
                    }
                }
            });
            client.resourceList(resources)
                  .inNamespace(namespace)
                  .serverSideApply();
        }
        if (process.exitValue() != 0) {
            process.getErrorStream().transferTo(System.out);
            throw new RuntimeException("Kustomize exited with status " + process.exitValue());
        }
    }
}

package com.brandwatch.kafka_pod_autoscaler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.testcontainers.k3s.K3sContainer.KUBE_SECURE_PORT;
import static org.testcontainers.k3s.K3sContainer.RANCHER_WEBHOOK_PORT;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
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

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.testing.FileConsumer;
import com.brandwatch.kafka_pod_autoscaler.testing.KustomizeHelper;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.KafkaPodAutoscaler;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.KafkaPodAutoscalerSpec;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerspec.ScaleTargetRef;

@Slf4j
@Timeout(value = 4, unit = TimeUnit.MINUTES)
@Testcontainers(parallel = true)
class KafkaPodAutoscalerIT {
    private static final String OPERATOR_NAMESPACE = "system-kpa";
    private static final String IMAGE_NAME = System.getProperty("imageName");
    public static final int POD_STARTUP_TIMEOUT = 240;
    private static final int STATUS_CHECK_TIMEOUT = 60;
    private static final int REGISTRY_PORT = 5000;
    private static final Map<String, Map<String, LogWatch>> watchLogs = new ConcurrentHashMap<>();

    @Container
    public static K3sContainer k3s = new K3sContainer(DockerImageName.parse("rancher/k3s:v1.24.16-k3s1"))
            .withNetwork(Network.newNetwork())
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("/k3s-registries.yaml"),
                    "/etc/rancher/k3s/registries.yaml"
            )
            // Expose port 5005 for debugging the operator.  To use:
            // 1. `docker ps` -> note forwarded_port for 5005, and container_name
            // 2. `docker exec -it <container_name> kubectl -n system-kpa port-forward --address 0.0.0.0 deployment/kafka-pod-autoscaler 5005:5005`
            // 3. Connect your debugger to localhost:<forwarded_port>
            .withExposedPorts(KUBE_SECURE_PORT, RANCHER_WEBHOOK_PORT, 5005)
            .withLogConsumer(new FileConsumer(Path.of("target/k3s.log")));
    @Container
    public static final GenericContainer<?> registryContainer = new GenericContainer<>(DockerImageName.parse("registry:2.8"))
            .withEnv("REGISTRY_HTTP_SECRET", "secret")
            .withNetwork(k3s.getNetwork())
            .withNetworkAliases("registry")
            .withExposedPorts(REGISTRY_PORT)
            .withPrivilegedMode(true)
            .waitingFor(Wait.forHttp("/v2/_catalog").forStatusCode(200))
            .withLogConsumer(new FileConsumer(Path.of("target/registry.log")));

    private String namespace;
    private static KubernetesClient client;

    @BeforeAll
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
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
        ref.setApiVersion("apps/v1");
        ref.setKind("Deployment");
        ref.setName("does-not-exist");
        var spec = new KafkaPodAutoscalerSpec();
        spec.setScaleTargetRef(ref);
        spec.setTriggers(List.of());
        res.setSpec(spec);

        client.resource(res).create();
        assertAutoscalerStatus("test", "Deployment not found. Skipping scale");
    }

    @Test
    public void canScaleStatically() throws IOException, InterruptedException {
        logger.info("Deploying test workload");
        applyKustomize("src/test/resources/workload/static");

        waitForPodsWithLabel("app", "statically-scaled", 1);

        applyKustomize("src/test/resources/autoscaler/static");

        waitForPodsWithLabel("app", "statically-scaled", 2);
        assertAutoscalerStatus("static-autoscaler", "Deployment being scaled from 1 to 2 replicas");
    }

    @Test
    public void canScaleStatically_statefulset() throws IOException, InterruptedException {
        logger.info("Deploying test workload");
        applyKustomize("src/test/resources/workload/static_statefulset");

        waitForPodsWithLabel("app", "statically-scaled-statefulset", 1);

        applyKustomize("src/test/resources/autoscaler/static_statefulset");

        waitForPodsWithLabel("app", "statically-scaled-statefulset", 2);
        assertAutoscalerStatus("static-autoscaler-statefulset", "StatefulSet being scaled from 1 to 2 replicas");
    }

    @Test
    public void doesNotScaleIfReplicasSetToZero() throws IOException, InterruptedException {
        logger.info("Deploying test workload");
        applyKustomize("src/test/resources/workload/static");

        waitForPodsWithLabel("app", "statically-scaled", 1);

        client.apps().deployments().inNamespace(namespace).withName("statically-scaled").scale(0);

        applyKustomize("src/test/resources/autoscaler/static");

        assertAutoscalerStatus("static-autoscaler", "Deployment has been scaled to zero. Skipping scale");
        assertThat(client.apps().deployments().inNamespace(namespace).withName("statically-scaled").get().getSpec().getReplicas())
                .isEqualTo(0);
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
        await().atMost(Duration.ofSeconds(STATUS_CHECK_TIMEOUT)).untilAsserted(() -> {
            var autoscaler = client.resources(KafkaPodAutoscaler.class).inNamespace(namespace).withName(name).get();

            assertThat(autoscaler.getStatus()).isNotNull();
            assertThat(autoscaler.getStatus().getMessage()).isEqualTo(expectedStatus);
        });
    }

    private static void deployOperator() throws IOException, InterruptedException {
        KustomizeHelper.applyKustomize(client, "src/test/resources/operator", OPERATOR_NAMESPACE);

        waitForPodsWithLabel(OPERATOR_NAMESPACE, "app", "kafka-pod-autoscaler", 1);
    }

    void applyKustomize(String path) throws IOException, InterruptedException {
        KustomizeHelper.applyKustomize(client, path, namespace);
    }
}

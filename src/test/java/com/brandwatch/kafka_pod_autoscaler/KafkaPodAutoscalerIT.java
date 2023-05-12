package com.brandwatch.kafka_pod_autoscaler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import brandwatch.com.v1alpha1.KafkaPodAutoscaler;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.testing.FileConsumer;

@Slf4j
@Testcontainers
class KafkaPodAutoscalerIT {
    private static final String IMAGE_NAME = System.getProperty("imageName");
    public static final int POD_STARTUP_TIMEOUT = 60;
    private static final int REGISTRY_PORT = 5000;

    public static final String TEST_RESOURCE_NAME = "test1";

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

    private String namespace;
    private KubernetesClient client;

    @BeforeAll
    static void beforeAll() throws Exception {
        // Use jib to upload the image to the temp registry
        Jib.from(DockerDaemonImage.named(IMAGE_NAME))
           .containerize(
                   Containerizer.to(RegistryImage.named("localhost:" + registryContainer.getMappedPort(REGISTRY_PORT) + "/kafka-pod-autoscaler"))
                                .setAllowInsecureRegistries(true)
           );
    }

    @BeforeEach
    void setup() {
        namespace = "kpa-it-" + UUID.randomUUID();

        // Use Config.fromKubeconfig instead of passing the raw string to withConfig,
        // as withConfig uses your local kubeconfig file whether you like it or not
        var config = Config.fromKubeconfig(k3s.getKubeConfigYaml());
        client = new KubernetesClientBuilder().withConfig(config).build();

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
        client.namespaces().resource(new NamespaceBuilder().withNewMetadata().withName(namespace)
                                                           .endMetadata().build()).delete();
        await()
                .atMost(Duration.ofSeconds(60))
                .untilAsserted(() -> assertThat(client.namespaces().withName(namespace).get()).isNull());
    }

    @Test
    void canDeployOperator() throws IOException {
        logger.info("Deploying operator");
        deployOperator();
        logger.info("Applying custom resource");
        applyCustomResource();
    }

    private void applyCustomResource() {
        var res = new KafkaPodAutoscaler();
        res.setMetadata(new ObjectMetaBuilder()
                                .withName(TEST_RESOURCE_NAME)
                                .withNamespace(namespace)
                                .build());
        client.resource(res).create();
    }

    private void deployOperator() throws IOException {
        var process = Runtime.getRuntime().exec("kustomize build src/test/resources/operator");

        try (var inputStream = process.getInputStream()) {
            applyResources(inputStream);
        }
        if (process.exitValue() != 0) {
            process.getErrorStream().transferTo(System.out);
            throw new RuntimeException("Kustomize exited with status " + process.exitValue());
        }
        await().atMost(Duration.ofSeconds(POD_STARTUP_TIMEOUT)).untilAsserted(() -> {
            var pod = client.pods().inNamespace(namespace).withLabel("app", "kafka-pod-autoscaler").list().getItems().get(0);
            assertThat(pod.getStatus().getContainerStatuses()).isNotEmpty();
            assertThat(pod.getStatus().getContainerStatuses().get(0).getReady()).isTrue();
        });
    }

    void applyResources(InputStream yamlStream) {
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
}

package com.brandwatch.kafka_pod_autoscaler;

import static io.javaoperatorsdk.operator.junit.AbstractOperatorExtension.CRD_READY_WAIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

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
    public static final int POD_STARTUP_TIMEOUT = 60;

    public static final String TEST_RESOURCE_NAME = "test1";

    @Container
    public static K3sContainer k3s = new K3sContainer(DockerImageName.parse("rancher/k3s:v1.23.17-k3s1"))
            .withLogConsumer(new FileConsumer(Path.of("target/k3s.log")));
    private String namespace;
    private KubernetesClient client;

    @BeforeEach
    void setup() {
        namespace = "kpa-it-" + UUID.randomUUID();

        // Use Config.fromKubeconfig instead of passing the raw string to withConfig,
        // as withConfig uses your local kubeconfig file whether you like it or not
        var config = Config.fromKubeconfig(k3s.getKubeConfigYaml());
        client = new KubernetesClientBuilder().withConfig(config).build();

        // Apply the CRD t the cluster
        String path = "./src/main/resources/kubernetes/kafkapodautoscaler.brandwatch.com-v1alpha1.yml";
        try (InputStream is = new FileInputStream(path)) {
            final var crd = client.load(is);
            crd.createOrReplace();
            Thread.sleep(CRD_READY_WAIT);
            logger.debug("Applied CRD with name: {}", crd.get().get(0).getMetadata().getName());
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }

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
    public void sanityCheck() {
        assertThat(client.namespaces().withName(namespace).get()).isNotNull();
    }

    @Test
    @Disabled
    void canScale() {
        logger.info("Applying custom resource");
        applyCustomResource();
        logger.info("Deploying operator");
        deployOperator();

    }

    private void applyCustomResource() {
        var res = new KafkaPodAutoscaler();
        res.setMetadata(new ObjectMetaBuilder()
                                .withName(TEST_RESOURCE_NAME)
                                .withNamespace(namespace)
                                .build());
        client.resource(res).create();
    }

    private void deployOperator() {
        applyResources("./k8s/operator.yaml");
        await().atMost(Duration.ofSeconds(POD_STARTUP_TIMEOUT)).untilAsserted(() -> {
            var pod = client.pods().inNamespace(namespace).withLabel("app", "kafka-pod-autoscaler").list().getItems().get(0);
            assertThat(pod.getStatus().getContainerStatuses().get(0).getReady()).isTrue();
        });
    }

    void applyResources(String path) {
        try {
            List<HasMetadata> resources = client.load(new FileInputStream(path)).items();
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
                  .createOrReplace();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}

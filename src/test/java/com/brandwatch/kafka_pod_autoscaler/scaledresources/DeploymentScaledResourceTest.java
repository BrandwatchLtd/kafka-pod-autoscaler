package com.brandwatch.kafka_pod_autoscaler.scaledresources;

import static com.brandwatch.kafka_pod_autoscaler.testing.KustomizeHelper.applyKustomize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.k3s.K3sContainer.KUBE_SECURE_PORT;
import static org.testcontainers.k3s.K3sContainer.RANCHER_WEBHOOK_PORT;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import com.brandwatch.kafka_pod_autoscaler.testing.FileConsumer;

@Timeout(value = 4, unit = TimeUnit.MINUTES)
@Testcontainers(parallel = true)
class DeploymentScaledResourceTest {
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
    private static KubernetesClient client;
    private String namespace;

    @BeforeAll
    public static void beforeAll() {
        // Use Config.fromKubeconfig instead of passing the raw string to withConfig,
        // as withConfig uses your local kubeconfig file whether you like it or not
        var config = Config.fromKubeconfig(k3s.getKubeConfigYaml());
        client = new KubernetesClientBuilder().withConfig(config).build();
    }

    @BeforeEach
    void setup() {
        namespace = "kpa-it-" + UUID.randomUUID();

        client.namespaces()
              .resource(new NamespaceBuilder().withNewMetadata().withName(namespace)
                                              .endMetadata().build())
              .create();
    }

    @Test
    public void canGetPods() throws IOException, InterruptedException {
        applyKustomize(client, "src/test/resources/workload/static", namespace);

        var deployment = client.apps().deployments().inNamespace(namespace).withName("statically-scaled");
        deployment.scale(2);

        var deploymentScaledResource = new DeploymentScaledResource(client, deployment);

        assertThat(deploymentScaledResource.pods()).hasSize(2);
    }
}

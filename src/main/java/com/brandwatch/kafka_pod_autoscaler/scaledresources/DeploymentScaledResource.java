package com.brandwatch.kafka_pod_autoscaler.scaledresources;

import java.util.List;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;

import com.brandwatch.kafka_pod_autoscaler.ScaledResource;

public class DeploymentScaledResource implements ScaledResource {
    private final KubernetesClient client;
    private final RollableScalableResource<Deployment> deployment;

    public DeploymentScaledResource(KubernetesClient client, RollableScalableResource<Deployment> deployment) {
        this.client = client;
        this.deployment = deployment;
    }

    @Override
    public boolean isReady() {
        return deployment.isReady();
    }

    @Override
    public int getReplicaCount() {
        return deployment.get().getSpec().getReplicas();
    }

    @Override
    public void scale(int bestReplicaCount) {
        deployment.scale(bestReplicaCount);
    }

    @Override
    public List<Pod> pods() {
        return client.pods()
                     .inNamespace(deployment.get().getMetadata().getNamespace())
                     .withLabelSelector(deployment.get().getSpec().getSelector())
                     .list()
                     .getItems();
    }
}

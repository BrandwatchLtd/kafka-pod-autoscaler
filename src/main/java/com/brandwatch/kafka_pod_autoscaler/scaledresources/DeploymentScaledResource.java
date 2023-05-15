package com.brandwatch.kafka_pod_autoscaler.scaledresources;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;

import com.brandwatch.kafka_pod_autoscaler.ScaledResource;

public class DeploymentScaledResource implements ScaledResource {
    private final RollableScalableResource<Deployment> deployment;

    public DeploymentScaledResource(RollableScalableResource<Deployment> deployment) {
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
}

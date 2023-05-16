package com.brandwatch.kafka_pod_autoscaler.scaledresources;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;

import com.brandwatch.kafka_pod_autoscaler.ScaledResource;

public class StatefulSetScaledResource implements ScaledResource {
    private final RollableScalableResource<StatefulSet> resource;

    public StatefulSetScaledResource(RollableScalableResource<StatefulSet> resource) {
        this.resource = resource;
    }

    @Override
    public boolean isReady() {
        return resource.isReady();
    }

    @Override
    public int getReplicaCount() {
        return resource.get().getSpec().getReplicas();
    }

    @Override
    public void scale(int bestReplicaCount) {
        resource.scale(bestReplicaCount);
    }
}

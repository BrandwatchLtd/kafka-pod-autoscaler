package com.brandwatch.kafka_pod_autoscaler.scaledresources;

import java.util.List;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;

import com.brandwatch.kafka_pod_autoscaler.ScaledResource;

public class StatefulSetScaledResource implements ScaledResource {
    private final KubernetesClient client;
    private final RollableScalableResource<StatefulSet> resource;

    public StatefulSetScaledResource(KubernetesClient client, RollableScalableResource<StatefulSet> resource) {
        this.client = client;
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

    @Override
    public List<Pod> pods() {
        return client.pods().withLabelSelector(resource.get().getSpec().getSelector()).list().getItems();
    }
}

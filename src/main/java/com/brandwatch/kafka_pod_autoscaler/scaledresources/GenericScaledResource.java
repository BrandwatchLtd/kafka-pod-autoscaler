package com.brandwatch.kafka_pod_autoscaler.scaledresources;

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.ScaledResource;

@Slf4j
public class GenericScaledResource implements ScaledResource {
    private final KubernetesClient client;
    private final Resource<GenericKubernetesResource> resource;

    public GenericScaledResource(KubernetesClient client, Resource<GenericKubernetesResource> resource) {
        this.client = client;
        this.resource = resource;
    }

    @Override
    public boolean isReady() {
        // fabric8 Readiness class reports true for these always anyway
        return true;
    }

    @Override
    public int getReplicaCount() {
        return Integer.parseInt(String.valueOf(((Map) resource.get().get("spec")).get("replicas")));
    }

    @Override
    public void scale(int bestReplicaCount) {
        resource.scale(bestReplicaCount);
    }

    @Override
    public List<Pod> pods() {
        throw new UnsupportedOperationException();
    }
}

package com.brandwatch.kafka_pod_autoscaler.scaledresources;

import java.util.Map;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.ScaledResource;

@Slf4j
public class GenericScaledResource implements ScaledResource {
    private final Resource<GenericKubernetesResource> resource;

    public GenericScaledResource(Resource<GenericKubernetesResource> resource) {
        this.resource = resource;
    }

    @Override
    public boolean isReady() {
        logger.debug("Resource ready={}: {}", resource.isReady(), resource);
        return resource.isReady();
    }

    @Override
    public int getReplicaCount() {
        return Integer.parseInt(String.valueOf(((Map) resource.get().get("spec")).get("replicas")));
    }

    @Override
    public void scale(int bestReplicaCount) {
        resource.scale(bestReplicaCount);
    }
}

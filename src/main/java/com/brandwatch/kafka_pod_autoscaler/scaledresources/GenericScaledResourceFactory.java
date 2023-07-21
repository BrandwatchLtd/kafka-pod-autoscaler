package com.brandwatch.kafka_pod_autoscaler.scaledresources;

import com.google.auto.service.AutoService;

import com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerspec.ScaleTargetRef;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.ScaledResource;
import com.brandwatch.kafka_pod_autoscaler.ScaledResourceFactory;

@Slf4j
@AutoService(ScaledResourceFactory.class)
public class GenericScaledResourceFactory implements ScaledResourceFactory {
    @Override
    public boolean supports(KubernetesClient client, String namespace, ScaleTargetRef scaleTargetRef) {
        return true;
    }

    @Override
    public ScaledResource create(KubernetesClient client, String namespace, ScaleTargetRef scaleTargetRef) {
        var resource = getApiResource(client, namespace, scaleTargetRef);
        if (resource.get() == null) {
            return null;
        }
        return new GenericScaledResource(client, resource);
    }

    private Resource<GenericKubernetesResource> getApiResource(KubernetesClient client, String namespace, ScaleTargetRef scaleTargetRef) {
        var apiVersion = scaleTargetRef.getApiVersion();
        var kind = scaleTargetRef.getKind();

        logger.debug("Fetching generic resource apiVersion={}, kind={}", apiVersion, kind);
        return client.genericKubernetesResources(apiVersion, kind)
                     .inNamespace(namespace)
                     .withName(scaleTargetRef.getName());
    }

}

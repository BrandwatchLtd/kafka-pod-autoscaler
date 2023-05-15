package com.brandwatch.kafka_pod_autoscaler.scaledresources;

import java.util.stream.Stream;

import com.google.auto.service.AutoService;

import brandwatch.com.v1alpha1.kafkapodautoscalerspec.ScaleTargetRef;
import io.fabric8.kubernetes.api.model.APIResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;

import com.brandwatch.kafka_pod_autoscaler.ScaledResource;
import com.brandwatch.kafka_pod_autoscaler.ScaledResourceFactory;

@AutoService(ScaledResourceFactory.class)
public class GenericScaledResourceFactory implements ScaledResourceFactory {
    @Override
    public boolean supports(KubernetesClient client, String namespace, ScaleTargetRef scaleTargetRef) {
        return true;
    }

    @Override
    public ScaledResource create(KubernetesClient client, String namespace, ScaleTargetRef scaleTargetRef) {
        return new GenericScaledResource(getApiResource(client, namespace, scaleTargetRef));
    }

    private Resource<GenericKubernetesResource> getApiResource(KubernetesClient client, String namespace, ScaleTargetRef scaleTargetRef) {
        var apiVersion = scaleTargetRef.getApiVersion();
        var kind = scaleTargetRef.getKind();
        var resource = getApiResources(client, apiVersion)
                .filter(r -> r.getKind().equals(kind) || r.getName().equals(kind) || r.getSingularName().equals(kind) || r.getShortNames().contains(kind))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find api-resource of type " + apiVersion + "/" + kind));
        return client.genericKubernetesResources(ResourceDefinitionContext.fromApiResource(apiVersion, resource))
                     .inNamespace(namespace)
                     .withName(scaleTargetRef.getName());
    }

    private Stream<APIResource> getApiResources(KubernetesClient client, String apiVersion) {
        if (apiVersion != null) {
            return client.getApiResources(apiVersion).getResources().stream();
        }
        return client.getApiGroups().getGroups().stream()
                     .flatMap(group -> group.getVersions().stream().flatMap(gv -> {
                         var apiResourceList = client.getApiResources(gv.getGroupVersion());
                         return apiResourceList.getResources()
                                               .stream()
                                               .filter(r -> !r.getName().contains("/"));
                     }));
    }
}

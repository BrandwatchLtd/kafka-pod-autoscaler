package com.brandwatch.kafka_pod_autoscaler.scaledresources;

import java.net.HttpURLConnection;

import com.google.auto.service.AutoService;

import brandwatch.com.v1alpha1.kafkapodautoscalerspec.ScaleTargetRef;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.ScaledResource;
import com.brandwatch.kafka_pod_autoscaler.ScaledResourceFactory;

@Slf4j
@AutoService(ScaledResourceFactory.class)
public class DeploymentResourceFactory implements ScaledResourceFactory {
    @Override
    public boolean supports(KubernetesClient client, String namespace, ScaleTargetRef scaleTargetRef) {
        return scaleTargetRef.getKind().equalsIgnoreCase("deployment");
    }

    @Override
    public ScaledResource create(KubernetesClient client, String namespace, ScaleTargetRef scaleTargetRef) {
        var resource = client.apps().deployments().inNamespace(namespace).withName(scaleTargetRef.getName());

        try {
            if (resource.get() == null) {
                return null;
            }

            logger.debug("Deployment {} exists!", scaleTargetRef.getName());
        } catch (KubernetesClientException e) {
            if (e.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                return null;
            }
            throw e;
        }

        return new DeploymentScaledResource(client, resource);
    }
}

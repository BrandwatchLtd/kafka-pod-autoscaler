package com.brandwatch.kafka_pod_autoscaler;

import brandwatch.com.v1alpha1.kafkapodautoscalerspec.ScaleTargetRef;
import io.fabric8.kubernetes.client.KubernetesClient;

public interface ScaledResourceFactory {
    boolean supports(KubernetesClient client, String namespace, ScaleTargetRef scaleTargetRef);

    ScaledResource create(KubernetesClient client, String namespace, ScaleTargetRef scaleTargetRef);
}

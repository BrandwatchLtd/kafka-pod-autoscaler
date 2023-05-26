package com.brandwatch.kafka_pod_autoscaler.triggers;

import brandwatch.com.v1alpha1.KafkaPodAutoscaler;
import brandwatch.com.v1alpha1.kafkapodautoscalerspec.Triggers;
import io.fabric8.kubernetes.client.KubernetesClient;

import com.brandwatch.kafka_pod_autoscaler.ScaledResource;

public interface TriggerProcessor {
    String getType();

    TriggerResult process(KubernetesClient client, ScaledResource resource, KafkaPodAutoscaler autoscaler, Triggers trigger, int replicaCount);
}

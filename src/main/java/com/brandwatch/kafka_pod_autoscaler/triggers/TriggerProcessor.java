package com.brandwatch.kafka_pod_autoscaler.triggers;

import io.fabric8.kubernetes.client.KubernetesClient;

import com.brandwatch.kafka_pod_autoscaler.ScaledResource;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.KafkaPodAutoscaler;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerspec.TriggerDefinition;

public interface TriggerProcessor {
    String getType();

    TriggerResult process(KubernetesClient client, ScaledResource resource, KafkaPodAutoscaler autoscaler, TriggerDefinition trigger, int replicaCount);
}

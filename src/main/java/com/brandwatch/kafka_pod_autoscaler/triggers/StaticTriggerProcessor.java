package com.brandwatch.kafka_pod_autoscaler.triggers;

import static java.util.Objects.requireNonNull;

import com.google.auto.service.AutoService;

import io.fabric8.kubernetes.client.KubernetesClient;

import com.brandwatch.kafka_pod_autoscaler.ScaledResource;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.KafkaPodAutoscaler;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerspec.TriggerDefinition;

@AutoService(TriggerProcessor.class)
public class StaticTriggerProcessor implements TriggerProcessor {
    @Override
    public String getType() {
        return "static";
    }

    @Override
    public TriggerResult process(KubernetesClient client, ScaledResource resource, KafkaPodAutoscaler autoscaler, TriggerDefinition trigger, int replicaCount) {
        var replicas = Integer.parseInt(requireNonNull(trigger.getMetadata().get("replicas")));
        return new TriggerResult(trigger, replicas, replicaCount);
    }
}

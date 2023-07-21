package com.brandwatch.kafka_pod_autoscaler.triggers;

import com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerspec.Triggers;

public record TriggerResult(Triggers trigger, double inputValue, double targetThreshold) {
    public int recommendedReplicas(int currentReplicaCount) {
        return (int) Math.ceil(currentReplicaCount * (inputValue / targetThreshold));
    }
}

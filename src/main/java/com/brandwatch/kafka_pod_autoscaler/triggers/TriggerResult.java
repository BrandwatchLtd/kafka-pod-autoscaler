package com.brandwatch.kafka_pod_autoscaler.triggers;

import brandwatch.com.v1alpha1.kafkapodautoscalerspec.Triggers;

public record TriggerResult(Triggers trigger, long inputValue, long targetThreshold) {
    public int recommendedReplicas(int currentReplicaCount) {
        return (int) Math.ceil(currentReplicaCount * (inputValue / (double) targetThreshold));
    }
}

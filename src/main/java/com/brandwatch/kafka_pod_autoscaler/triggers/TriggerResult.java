package com.brandwatch.kafka_pod_autoscaler.triggers;

import com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerspec.TriggerDefinition;

public record TriggerResult(TriggerDefinition trigger, double inputValue, double targetThreshold, boolean inverted) {
    public TriggerResult(TriggerDefinition trigger, double inputValue, double targetThreshold) {
        this(trigger, inputValue, targetThreshold, false);
    }

    public int recommendedReplicas(int currentReplicaCount) {
        if (inverted) {
            return (int) Math.ceil(currentReplicaCount * (targetThreshold / inputValue));
        }
        return (int) Math.ceil(currentReplicaCount * (inputValue / targetThreshold));
    }

    public static TriggerResult inverted(TriggerDefinition trigger, double inputValue, double targetThreshold) {
        return new TriggerResult(trigger, inputValue, targetThreshold, true);
    }
}

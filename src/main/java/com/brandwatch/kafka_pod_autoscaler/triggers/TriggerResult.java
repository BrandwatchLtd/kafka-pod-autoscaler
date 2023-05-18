package com.brandwatch.kafka_pod_autoscaler.triggers;

import brandwatch.com.v1alpha1.kafkapodautoscalerspec.Triggers;

public record TriggerResult(Triggers trigger, int recommendedReplicas) {
}

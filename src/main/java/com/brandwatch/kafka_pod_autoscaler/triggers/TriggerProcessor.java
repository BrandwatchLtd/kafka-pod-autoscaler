package com.brandwatch.kafka_pod_autoscaler.triggers;

import brandwatch.com.v1alpha1.KafkaPodAutoscaler;
import brandwatch.com.v1alpha1.kafkapodautoscalerspec.Triggers;

public interface TriggerProcessor {
    String getType();

    TriggerResult process(KafkaPodAutoscaler autoscaler, Triggers trigger, int currentReplicaCount);
}

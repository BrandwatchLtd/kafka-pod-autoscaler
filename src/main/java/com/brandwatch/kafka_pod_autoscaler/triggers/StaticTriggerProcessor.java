package com.brandwatch.kafka_pod_autoscaler.triggers;

import com.google.auto.service.AutoService;

import brandwatch.com.v1alpha1.KafkaPodAutoscaler;
import brandwatch.com.v1alpha1.kafkapodautoscalerspec.Triggers;

@AutoService(TriggerProcessor.class)
public class StaticTriggerProcessor implements TriggerProcessor {
    @Override
    public String getType() {
        return "static";
    }

    @Override
    public TriggerResult process(KafkaPodAutoscaler autoscaler, Triggers trigger, int currentReplicaCount) {
        return new TriggerResult(trigger, Integer.parseInt(trigger.getMetadata().get("replicas")));
    }
}

package com.brandwatch.kafka_pod_autoscaler.triggers;

import com.google.auto.service.AutoService;

import brandwatch.com.v1alpha1.kafkapodautoscalerspec.Triggers;

@AutoService(TriggerProcessor.class)
public class StaticTriggerProcessor implements TriggerProcessor {
    @Override
    public String getType() {
        return "static";
    }

    @Override
    public TriggerResult process(Triggers trigger) {
        return new TriggerResult(trigger, Integer.parseInt(trigger.getMetadata().get("replicas")));
    }
}

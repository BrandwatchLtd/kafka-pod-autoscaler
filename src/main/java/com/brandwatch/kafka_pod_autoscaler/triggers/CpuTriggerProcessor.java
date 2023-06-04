package com.brandwatch.kafka_pod_autoscaler.triggers;

import static java.util.Objects.requireNonNull;

import com.google.auto.service.AutoService;

import brandwatch.com.v1alpha1.KafkaPodAutoscaler;
import brandwatch.com.v1alpha1.kafkapodautoscalerspec.Triggers;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.ScaledResource;

@Slf4j
@AutoService(TriggerProcessor.class)
public class CpuTriggerProcessor implements TriggerProcessor {
    @Override
    public String getType() {
        return "cpu";
    }

    @Override
    public TriggerResult process(KubernetesClient client, ScaledResource resource, KafkaPodAutoscaler autoscaler, Triggers trigger, int replicaCount) {
        var threshold = Long.parseLong(requireNonNull(trigger.getMetadata().get("threshold")));
        var cpu = resource.pods().stream()
            .map(pod -> client.top().pods().metrics(pod.getMetadata().getNamespace(), pod.getMetadata().getName()))
            .flatMap(m -> m.getContainers().stream())
            .mapToDouble(c -> c.getUsage().get("cpu").getNumericalAmount().doubleValue())
            .sum();
        var cpuRequest = resource.pods().stream()
                          .flatMap(pod -> pod.getSpec().getContainers().stream().map(c -> c.getResources().getRequests()))
                          .mapToDouble(c -> c.get("cpu").getNumericalAmount().doubleValue())
                          .sum();

        return new TriggerResult(trigger, (long) Math.ceil((cpu / cpuRequest) * 100), threshold);
    }
}

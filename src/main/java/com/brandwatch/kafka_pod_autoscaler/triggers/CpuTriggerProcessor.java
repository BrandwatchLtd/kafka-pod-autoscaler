package com.brandwatch.kafka_pod_autoscaler.triggers;

import static java.util.Objects.requireNonNull;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.auto.service.AutoService;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.ScaledResource;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.KafkaPodAutoscaler;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerspec.TriggerDefinition;

@Slf4j
@AutoService(TriggerProcessor.class)
public class CpuTriggerProcessor implements TriggerProcessor {
    private static final LoadingCache<String, Cache<Long, Double>> podCpuHistory = Caffeine
            .newBuilder()
            .expireAfterAccess(Duration.ofMinutes(5L))
            .build(key -> Caffeine.newBuilder()
                          .expireAfterAccess(Duration.ofMinutes(1L))
                          .build());

    @Override
    public String getType() {
        return "cpu";
    }

    @Override
    public TriggerResult process(KubernetesClient client, ScaledResource resource, KafkaPodAutoscaler autoscaler, TriggerDefinition trigger, int replicaCount) {
        var threshold = Long.parseLong(requireNonNull(trigger.getMetadata().get("threshold")));
        var cpu = resource.pods().stream()
            .mapToDouble(pod -> getAverageCpu(client, pod))
            .sum();
        var cpuRequest = resource.pods().stream()
                          .flatMap(pod -> pod.getSpec().getContainers().stream().map(c -> c.getResources().getRequests()))
                          .mapToDouble(c -> c.get("cpu").getNumericalAmount().doubleValue())
                          .sum();

        var cpuPercent = (long) Math.ceil((cpu / cpuRequest) * 100);
        logger.debug("Calculating CPU trigger with values: cpu={}, cpuRequest={}, cpuPercent={}, threshold={}",
                     cpu, cpuRequest, cpuPercent, threshold);
        return new TriggerResult(trigger, cpuPercent, threshold);
    }

    private Double getAverageCpu(KubernetesClient client, Pod pod) {
        var key = pod.getMetadata().getNamespace() + "/" + pod.getMetadata().getName();
        var podCpu = client.top().pods().metrics(pod.getMetadata().getNamespace(), pod.getMetadata().getName())
                .getContainers().stream()
                .mapToDouble(c -> c.getUsage().get("cpu").getNumericalAmount().doubleValue())
                .sum();

        var historicalCpu = podCpuHistory.get(key);
        historicalCpu.put(System.currentTimeMillis(), podCpu);

        return historicalCpu.asMap().values().stream()
                            .mapToDouble(v -> v)
                            .average()
                            .orElse(0);
    }
}

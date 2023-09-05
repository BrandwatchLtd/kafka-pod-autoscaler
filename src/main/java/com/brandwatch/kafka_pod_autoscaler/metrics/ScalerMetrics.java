package com.brandwatch.kafka_pod_autoscaler.metrics;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.util.concurrent.AtomicDouble;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import lombok.NonNull;

import com.brandwatch.kafka_pod_autoscaler.triggers.TriggerResult;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.KafkaPodAutoscaler;

public class ScalerMetrics {
    private static final Map<String, ScalerMetrics> metrics = new ConcurrentHashMap<>();
    private final Tags tags;
    private final AtomicInteger partitionCount;
    private final AtomicInteger currentReplicaCount;
    private final AtomicInteger calculatedReplicaCount;
    private final AtomicInteger finalReplicaCount;
    private final AtomicInteger dryRunReplicas;
    private final AtomicLong notReady;
    private final AtomicLong lastScale;
    private final AtomicInteger scalable;
    private final Map<String, AtomicDouble> triggerValueMetrics = new ConcurrentHashMap<>();
    private final Map<String, AtomicDouble> triggerThresholdMetrics = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> triggerReplicaMetrics = new ConcurrentHashMap<>();

    public ScalerMetrics(@NonNull KafkaPodAutoscaler kpa) {
        var kpaName = kpa.getMetadata().getName();
        var kpaNamespace = kpa.getMetadata().getNamespace();
        var targetName = kpa.getSpec().getScaleTargetRef().getName();
        var targetKind = kpa.getSpec().getScaleTargetRef().getKind();
        var targetNamespace = kpaNamespace;  // kpa's must be in target namespace
        tags = Tags.of("kpa-namespace", kpaNamespace,
                       "kpa-name", kpaName,
                       "kpa-target-namespace", targetNamespace,
                       "kpa-target-name", targetName,
                       "kpa-target-kind", targetKind);
        partitionCount = Metrics.gauge("kpa_partition_count", tags, new AtomicInteger());
        currentReplicaCount = Metrics.gauge("kpa_current_replica_count", tags, new AtomicInteger());
        calculatedReplicaCount = Metrics.gauge("kpa_calculated_replica_count", tags, new AtomicInteger());
        finalReplicaCount = Metrics.gauge("kpa_final_replica_count", tags, new AtomicInteger());
        dryRunReplicas = Metrics.gauge("kpa_dry_run_replicas", tags, new AtomicInteger());
        notReady = Metrics.gauge("kpa_not_ready", tags, new AtomicLong());
        lastScale = Metrics.gauge("kpa_last_scale", tags, new AtomicLong());
        scalable = Metrics.gauge("kpa_scaleable", tags, new AtomicInteger());
    }

    public static ScalerMetrics getOrCreate(KafkaPodAutoscaler kpa) {
        var key = kpa.getMetadata().getNamespace() + "-" + kpa.getMetadata().getName();
        return metrics.computeIfAbsent(key, n -> new ScalerMetrics(kpa));
    }

    public void setPartitionCount(int partitionCount) {
        this.partitionCount.set(partitionCount);
    }

    public void setCurrentReplicaCount(int currentReplicaCount) {
        this.currentReplicaCount.set(currentReplicaCount);
    }

    public void setCalculatedReplicaCount(int calculatedReplicaCount) {
        this.calculatedReplicaCount.set(calculatedReplicaCount);
    }

    public void setFinalReplicaCount(int finalReplicaCount) {
        this.finalReplicaCount.set(finalReplicaCount);
    }

    public void setDryRunReplicas(Integer dryRunReplicas) {
        this.dryRunReplicas.set(Optional.ofNullable(dryRunReplicas).orElse(-1));
    }

    public void setNotReady(long notReady) {
        this.notReady.set(notReady);
    }

    public void setLastScale(long lastScale) {
        this.lastScale.set(lastScale);
    }

    public void setScalable(boolean scalable) {
        this.scalable.set(scalable ? 1 : 0);
    }

    public void setTriggerMetrics(TriggerResult result, int recommendedReplicas) {
        triggerValueMetrics.computeIfAbsent(result.trigger().getType(),
                                            type -> {
                                                var typeTags = tags.and("type", type);
                                                return Metrics.gauge("kpa_trigger_value", typeTags, new AtomicDouble());
                                            })
                           .set(result.inputValue());
        triggerThresholdMetrics.computeIfAbsent(result.trigger().getType(),
                                                type -> {
                                                    var typeTags = tags.and("type", type);
                                                    return Metrics.gauge("kpa_trigger_threshold", typeTags, new AtomicDouble());
                                                })
                               .set(result.targetThreshold());
        triggerReplicaMetrics.computeIfAbsent(result.trigger().getType(),
                                                type -> {
                                                    var typeTags = tags.and("type", type);
                                                    return Metrics.gauge("kpa_trigger_replicas", typeTags, new AtomicLong());
                                                })
                               .set(result.recommendedReplicas(recommendedReplicas));
    }

    public boolean isScalable() {
        return scalable.get() == 1;
    }
}

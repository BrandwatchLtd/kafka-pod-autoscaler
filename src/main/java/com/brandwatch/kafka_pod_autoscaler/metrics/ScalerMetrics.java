package com.brandwatch.kafka_pod_autoscaler.metrics;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;

public class ScalerMetrics {
    private static final Map<String, ScalerMetrics> metrics = new ConcurrentHashMap<>();
    private final AtomicInteger partitionCount;
    private final AtomicInteger currentReplicaCount;
    private final AtomicInteger calculatedReplicaCount;
    private final AtomicInteger finalReplicaCount;
    private final AtomicInteger dryRunReplicas;
    private final AtomicLong lastScale;

    public ScalerMetrics(String namespace, String name) {
        var tags = Tags.of("kpa-namespace", namespace, "kpa-name", name);
        partitionCount = Metrics.gauge("kpa_partition_count", tags, new AtomicInteger());
        currentReplicaCount = Metrics.gauge("kpa_current_replica_count", tags, new AtomicInteger());
        calculatedReplicaCount = Metrics.gauge("kpa_calculated_replica_count", tags, new AtomicInteger());
        finalReplicaCount = Metrics.gauge("kpa_final_replica_count", tags, new AtomicInteger());
        dryRunReplicas = Metrics.gauge("kpa_dry_run_replicas", tags, new AtomicInteger());
        lastScale = Metrics.gauge("kpa_last_scale", tags, new AtomicLong());
    }

    public static ScalerMetrics getOrCreate(String namespace, String name) {
        var key = namespace + "-" + name;
        return metrics.computeIfAbsent(key, n -> new ScalerMetrics(namespace, name));
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

    public void setLastScale(long lastScale) {
        this.lastScale.set(lastScale);
    }
}

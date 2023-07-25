package com.brandwatch.kafka_pod_autoscaler.triggers.kafka;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.apache.kafka.common.TopicPartition;

import com.google.common.annotations.VisibleForTesting;

public class LagMetrics {
    private final LongSupplier clock;
    private final Duration offsetRetention;

    private final Map<Integer, List<RecordedOffsets>> historicalConsumerOffsets = new ConcurrentHashMap<>();
    private final List<RecordedOffsets> historicalEndOffsets = new ArrayList<>();

    public LagMetrics() {
        this(System::currentTimeMillis);
    }

    @VisibleForTesting
    LagMetrics(LongSupplier clock) {
        this(clock, Duration.ofMinutes(5));
    }

    @VisibleForTesting
    LagMetrics(LongSupplier clock, Duration offsetRetention) {
        this.clock = clock;
        this.offsetRetention = offsetRetention;
    }

    public OptionalDouble calculateConsumerRate(int replicaCount, Map<TopicPartition, Long> consumerOffsets) {
        var historicalOffsets = historicalConsumerOffsets.get(replicaCount);

        // Choose the replicaCount "nearest" to this one if missing
        var substituteReplicaCount = replicaCount;
        if (historicalOffsets == null || historicalOffsets.isEmpty()) {
            substituteReplicaCount = findNearestReplicaCountTo(replicaCount);
            historicalOffsets = historicalConsumerOffsets.get(substituteReplicaCount);
        }

        if (historicalOffsets == null) {
            return OptionalDouble.empty();
        }

        // If we chose an alternative replica count, we need to scale it to match this replica count
        var scaleFactor = (substituteReplicaCount / (double) replicaCount);
        return calculateRate(historicalOffsets, new RecordedOffsets(clock.getAsLong(), consumerOffsets))
            .stream().map(d -> d * scaleFactor).findFirst();
    }

    public OptionalDouble calculateAndRecordTopicRate(Map<TopicPartition, Long> topicEndOffsets) {
        var offsetsToRecord = new RecordedOffsets(clock.getAsLong(), topicEndOffsets);
        var rate = calculateRate(historicalEndOffsets, offsetsToRecord);
        historicalEndOffsets.add(offsetsToRecord);
        pruneOffsets(historicalEndOffsets);
        return rate;
    }

    public OptionalDouble estimateLoadedConsumerRate(int replicaCount) {
        var historicalOffsets = historicalConsumerOffsets.get(replicaCount);

        // Choose the replicaCount "nearest" to this one if missing
        var substituteReplicaCount = replicaCount;
        if (historicalOffsets == null || historicalOffsets.isEmpty()) {
            substituteReplicaCount = findNearestReplicaCountTo(replicaCount);
            historicalOffsets = historicalConsumerOffsets.get(substituteReplicaCount);
        }

        if (historicalOffsets == null) {
            return OptionalDouble.empty();
        }

        // If we chose an alternative replica count, we need to scale it to match this replica count
        var scaleFactor = (substituteReplicaCount / (double) replicaCount);
        return calculateRate(historicalOffsets).stream().map(d -> d * scaleFactor).findFirst();
    }

    private int findNearestReplicaCountTo(int replicaCount) {
        var substituteReplicaCount = replicaCount;
        var replicaCountDelta = Integer.MAX_VALUE;
        for (var otherReplicaCount : historicalConsumerOffsets.keySet()) {
            var thisDelta = Math.abs(otherReplicaCount - replicaCount);
            if (thisDelta < replicaCountDelta) {
                substituteReplicaCount = otherReplicaCount;
                replicaCountDelta = thisDelta;
            }
        }
        return substituteReplicaCount;
    }

    public void recordConsumerRate(int replicaCount, Map<TopicPartition, Long> consumerOffsets) {
        var historical = historicalConsumerOffsets.computeIfAbsent(replicaCount, c -> new ArrayList<>());

        historical.add(new RecordedOffsets(clock.getAsLong(), consumerOffsets));

        pruneOffsets(historical);
    }

    private OptionalDouble calculateRate(List<RecordedOffsets> historicalOffsets) {
        var latestOffsets = historicalOffsets.stream()
            .max(Comparator.comparing(off -> off.timestamp));

        return latestOffsets.map(recordedOffsets -> calculateRate(historicalOffsets, recordedOffsets))
            .orElseGet(OptionalDouble::empty);
    }

    private OptionalDouble calculateRate(List<RecordedOffsets> historicalOffsets, RecordedOffsets latestOffsets) {
        var maybeEarliestOffsets = historicalOffsets.stream()
            .min(Comparator.comparing(off -> off.timestamp));

        if (maybeEarliestOffsets.isEmpty()) {
            return OptionalDouble.empty();
        }
        var earliestOffsets = maybeEarliestOffsets.get();

        var timestampDelta = latestOffsets.timestamp - earliestOffsets.timestamp;

        if (timestampDelta == 0) {
            return OptionalDouble.empty();
        }

        var smallestOffsetDelta = Long.MAX_VALUE;
        for (var partition : latestOffsets.offsets().keySet()) {
            if (!earliestOffsets.offsets().containsKey(partition)) {
                continue;
            }
            var partitionOffsetDelta = (latestOffsets.offsets().get(partition) - earliestOffsets.offsets().get(partition));
            if (partitionOffsetDelta > 0 && partitionOffsetDelta < smallestOffsetDelta) {
                smallestOffsetDelta = partitionOffsetDelta;
            }
        }

        if (smallestOffsetDelta == Long.MAX_VALUE) {
            return OptionalDouble.empty();
        }

        // Return the consumption rate in ops/sec
        return OptionalDouble.of(smallestOffsetDelta / (timestampDelta / 1000D));
    }

    private void pruneOffsets(List<RecordedOffsets> historicalOffsets) {
        // Always keep the most recent offsets
        var maybeMostRecentOffsets = historicalOffsets.stream()
            .max(Comparator.comparing(off -> off.timestamp));

        if (maybeMostRecentOffsets.isEmpty()) {
            return;
        }

        var cutoff = clock.getAsLong() - offsetRetention.toMillis();
        historicalOffsets.removeIf(off -> off.timestamp() < cutoff);

        if (!historicalOffsets.contains(maybeMostRecentOffsets.get())) {
            historicalOffsets.add(maybeMostRecentOffsets.get());
        }
    }

    record RecordedOffsets(long timestamp, Map<TopicPartition, Long> offsets) {
    }
}

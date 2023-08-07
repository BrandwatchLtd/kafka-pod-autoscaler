package com.brandwatch.kafka_pod_autoscaler.triggers.kafka;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.LongSupplier;

import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;
import org.apache.kafka.common.TopicPartition;

import com.google.common.annotations.VisibleForTesting;

import lombok.Getter;

public class TopicConsumerStats {
    private final LongSupplier clock;

    @Getter
    private long lag;
    private RecordedOffsets consumerOffsets;
    private RecordedOffsets topicEndOffsets;
    private final SynchronizedDescriptiveStatistics historicalConsumerRates = new SynchronizedDescriptiveStatistics();
    private final SynchronizedDescriptiveStatistics historicalTopicRates = new SynchronizedDescriptiveStatistics();

    public TopicConsumerStats() {
        this(System::currentTimeMillis);
    }

    @VisibleForTesting
    TopicConsumerStats(LongSupplier clock) {
        this.clock = clock;
        this.historicalConsumerRates.setWindowSize(360);
        this.historicalTopicRates.setWindowSize(360);
    }

    public void update(int replicaCount, Map<TopicPartition, Long> consumerOffsets, Map<TopicPartition, Long> topicEndOffsets) {
        var now = clock.getAsLong();
        this.lag = consumerOffsets.keySet().stream()
            .mapToLong(partition -> topicEndOffsets.get(partition) - consumerOffsets.get(partition))
            .sum();

        var newConsumerOffsets = new RecordedOffsets(now, consumerOffsets);
        var newTopicEndOffsets = new RecordedOffsets(now, topicEndOffsets);
        if (this.consumerOffsets != null) {
            calculateRate(this.consumerOffsets, newConsumerOffsets)
                .ifPresent(value -> historicalConsumerRates.addValue(value / (double) replicaCount));
        }
        if (this.topicEndOffsets != null) {
            calculateRate(this.topicEndOffsets, new RecordedOffsets(now, topicEndOffsets))
                .ifPresent(historicalTopicRates::addValue);
        }
        this.consumerOffsets = newConsumerOffsets;
        this.topicEndOffsets = newTopicEndOffsets;
    }

    public OptionalDouble getTopicRate() {
        if (historicalTopicRates.getN() < 3) {
            return OptionalDouble.empty();
        }
        var rate = historicalTopicRates.getPercentile(99D);

        if (!Double.isFinite(rate)) {
            return OptionalDouble.empty();
        }

        return OptionalDouble.of(rate);
    }

    public OptionalDouble estimateConsumerRate(int replicaCount) {
        if (historicalConsumerRates.getN() < 3) {
            return OptionalDouble.empty();
        }
        var rate = historicalConsumerRates.getPercentile(99D);

        if (!Double.isFinite(rate)) {
            return OptionalDouble.empty();
        }

        return OptionalDouble.of(rate * replicaCount);
    }

    private OptionalDouble calculateRate(RecordedOffsets earliestOffsets, RecordedOffsets latestOffsets) {
        var timestampDelta = latestOffsets.timestamp - earliestOffsets.timestamp;

        if (timestampDelta == 0) {
            return OptionalDouble.empty();
        }

        var messagesProcessed = 0L;
        for (var partition : latestOffsets.offsets().keySet()) {
            if (!earliestOffsets.offsets().containsKey(partition)) {
                continue;
            }
            messagesProcessed += (latestOffsets.offsets().get(partition) - earliestOffsets.offsets().get(partition));
        }

        // Return the consumption rate in ops/sec
        return OptionalDouble.of(messagesProcessed / (timestampDelta / 1000D));
    }

    record RecordedOffsets(long timestamp, Map<TopicPartition, Long> offsets) {
    }
}

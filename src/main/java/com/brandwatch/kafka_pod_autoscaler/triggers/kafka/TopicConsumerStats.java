package com.brandwatch.kafka_pod_autoscaler.triggers.kafka;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.LongSupplier;

import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;
import org.apache.kafka.common.TopicPartition;

import com.google.common.annotations.VisibleForTesting;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class TopicConsumerStats {
    private final LongSupplier clock;

    @Getter
    private long lag;
    private RecordedOffsets consumerOffsets;
    private RecordedOffsets topicEndOffsets;
    private final SynchronizedDescriptiveStatistics historicalConsumerRates = new SynchronizedDescriptiveStatistics();
    private final SynchronizedDescriptiveStatistics historicalTopicRates = new SynchronizedDescriptiveStatistics();
    @Getter
    @Setter
    private double consumerRatePercentile = 99D;
    @Getter
    @Setter
    private long minimumConsumerRateMeasurements = 3;
    @Getter
    @Setter
    private double topicRatePercentile = 99D;
    @Getter
    @Setter
    private long minimumTopicRateMeasurements = 3;
    @Getter
    @Setter
    private Duration consumerCommitTimeout = Duration.ofMinutes(1L);

    public TopicConsumerStats() {
        this(System::currentTimeMillis);
    }

    @VisibleForTesting
    TopicConsumerStats(LongSupplier clock) {
        this.clock = clock;
        this.historicalConsumerRates.setWindowSize(360);
        this.historicalTopicRates.setWindowSize(360);
    }

    public void setConsumerRateWindowSize(int windowSize) {
        this.historicalConsumerRates.setWindowSize(windowSize);
    }

    public void setTopicRateWindowSize(int windowSize) {
        this.historicalTopicRates.setWindowSize(windowSize);
    }

    public void update(int replicaCount, Map<TopicPartition, Long> consumerOffsets, Map<TopicPartition, Long> topicEndOffsets) {
        var now = clock.getAsLong();
        this.lag = consumerOffsets.keySet().stream()
            .mapToLong(partition -> topicEndOffsets.get(partition) - consumerOffsets.get(partition))
            .sum();

        var newConsumerOffsets = new RecordedOffsets(now, consumerOffsets);
        if (this.consumerOffsets == null
                || this.consumerOffsets.haveAllChangedSince(newConsumerOffsets)
                || this.consumerOffsets.haveTimedout(clock, this.consumerCommitTimeout)) {
            if (this.consumerOffsets != null) {
                calculateRate(this.consumerOffsets, newConsumerOffsets)
                    .ifPresent(value -> historicalConsumerRates.addValue(value / (double) replicaCount));
            }
            this.consumerOffsets = newConsumerOffsets;
        }

        var newTopicEndOffsets = new RecordedOffsets(now, topicEndOffsets);
        if (this.topicEndOffsets != null) {
            calculateRate(this.topicEndOffsets, newTopicEndOffsets)
                .ifPresent(historicalTopicRates::addValue);
        }
        this.topicEndOffsets = newTopicEndOffsets;
    }

    public OptionalDouble getTopicRate() {
        if (historicalTopicRates.getN() < minimumTopicRateMeasurements) {
            return OptionalDouble.empty();
        }
        var rate = historicalTopicRates.getPercentile(topicRatePercentile);

        if (!Double.isFinite(rate)) {
            return OptionalDouble.empty();
        }

        return OptionalDouble.of(rate);
    }

    public OptionalDouble estimateConsumerRate(int replicaCount) {
        if (historicalConsumerRates.getN() < minimumConsumerRateMeasurements) {
            return OptionalDouble.empty();
        }
        var rate = historicalConsumerRates.getPercentile(consumerRatePercentile);

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
        public boolean haveAllChangedSince(@NonNull RecordedOffsets otherOffsets) {
            return offsets.keySet().stream()
                .noneMatch(tp -> Objects.equals(otherOffsets.offsets.get(tp), offsets().get(tp)));
        }

        public boolean haveTimedout(@NonNull LongSupplier clock, @NonNull Duration consumerCommitTimeout) {
            return (timestamp + consumerCommitTimeout.toMillis()) < clock.getAsLong();
        }
    }
}

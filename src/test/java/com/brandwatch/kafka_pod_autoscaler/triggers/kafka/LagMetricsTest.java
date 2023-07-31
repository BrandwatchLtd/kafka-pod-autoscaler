package com.brandwatch.kafka_pod_autoscaler.triggers.kafka;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class LagMetricsTest {
    private static final Instant NOW = Instant.parse("2000-01-02T03:04:05.006Z");
    private static final TopicPartition PARTITION_1 = new TopicPartition("topic", 1);
    private static final TopicPartition PARTITION_2 = new TopicPartition("topic", 2);
    private static final TopicPartition PARTITION_3 = new TopicPartition("topic", 3);
    private static final TopicPartition PARTITION_4 = new TopicPartition("topic", 4);
    private static final Map<TopicPartition, Long> OFFSETS_1 = Map.of(
        PARTITION_1, 0L,
        PARTITION_2, 0L,
        PARTITION_3, 0L,
        PARTITION_4, 0L
    );
    private static final Map<TopicPartition, Long> OFFSETS_2 = Map.of(
        PARTITION_1, 1L,
        PARTITION_2, 1L,
        PARTITION_3, 1L,
        PARTITION_4, 1L
    );
    private static final Map<TopicPartition, Long> OFFSETS_3 = Map.of(
        PARTITION_1, 2L,
        PARTITION_2, 2L,
        PARTITION_3, 2L,
        PARTITION_4, 2L
    );

    @ParameterizedTest
    @MethodSource
    public void calculateConsumerRate(int previousReplicaCount,
                                      List<Map<TopicPartition, Long>> previousConsumerOffsets,
                                      Map<TopicPartition, Long> consumerOffsets,
                                      int calculateUsingReplicaCount,
                                      OptionalDouble expectedConsumerRate) {
        var clock = new AtomicLong(NOW.toEpochMilli());
        var lagMetrics = new LagMetrics(clock::get);

        for (var previousOffsets : previousConsumerOffsets) {
            lagMetrics.recordConsumerRate(previousReplicaCount, previousOffsets);
            clock.addAndGet(1_000);
        }

        var consumerRate = lagMetrics.calculateConsumerRate(calculateUsingReplicaCount, consumerOffsets);
        assertThat(consumerRate).isEqualTo(expectedConsumerRate);
    }

    public static Stream<Arguments> calculateConsumerRate() {
        return Stream.of(
            Arguments.of(1, List.of(), OFFSETS_1, 1, OptionalDouble.empty()),
            Arguments.of(1, List.of(OFFSETS_1), OFFSETS_1, 1, OptionalDouble.empty()),
            Arguments.of(1, List.of(OFFSETS_1), OFFSETS_2, 1, OptionalDouble.of(1D)),
            Arguments.of(1, List.of(OFFSETS_1), OFFSETS_2, 2, OptionalDouble.of(0.5D)),
            Arguments.of(2, List.of(OFFSETS_1), OFFSETS_2, 1, OptionalDouble.of(2D))
        );
    }

    @ParameterizedTest
    @MethodSource
    public void estimateLoadedConsumerRate(int previousReplicaCount,
                                      List<Map<TopicPartition, Long>> previousConsumerOffsets,
                                      Map<TopicPartition, Long> consumerOffsets,
                                      int calculateUsingReplicaCount,
                                      OptionalDouble expectedConsumerRate) {
        var clock = new AtomicLong(NOW.toEpochMilli());
        var lagMetrics = new LagMetrics(clock::get);

        for (var previousOffsets : previousConsumerOffsets) {
            lagMetrics.recordConsumerRate(previousReplicaCount, previousOffsets);
            clock.addAndGet(1_000);
        }

        var consumerRate = lagMetrics.estimateLoadedConsumerRate(calculateUsingReplicaCount, consumerOffsets);
        assertThat(consumerRate).isEqualTo(expectedConsumerRate);
    }

    public static Stream<Arguments> estimateLoadedConsumerRate() {
        return Stream.of(
            Arguments.of(1, List.of(), OFFSETS_1, 1, OptionalDouble.empty()),
            Arguments.of(1, List.of(OFFSETS_1), OFFSETS_1, 1, OptionalDouble.empty()),
            Arguments.of(1, List.of(OFFSETS_1), OFFSETS_2, 1, OptionalDouble.of(1D)),
            Arguments.of(1, List.of(OFFSETS_1), OFFSETS_2, 2, OptionalDouble.of(0.5D)),
            Arguments.of(1, List.of(OFFSETS_1, OFFSETS_1), OFFSETS_1, 1, OptionalDouble.empty()),
            Arguments.of(1, List.of(OFFSETS_1, OFFSETS_2), OFFSETS_2, 1, OptionalDouble.of(1D)),
            Arguments.of(1, List.of(OFFSETS_1, OFFSETS_2), OFFSETS_2, 2, OptionalDouble.of(0.5D)),
            Arguments.of(2, List.of(OFFSETS_1, OFFSETS_2), OFFSETS_2, 1, OptionalDouble.of(2D))
        );
    }

    @ParameterizedTest
    @MethodSource
    public void calculateAndRecordTopicRate(List<Map<TopicPartition, Long>> previousTopicEndOffsets,
                                      Map<TopicPartition, Long> topicEndOffsets,
                                      OptionalDouble expectedTopicRate) {
        var clock = new AtomicLong(NOW.toEpochMilli());
        var lagMetrics = new LagMetrics(clock::get);

        for (var previousOffsets : previousTopicEndOffsets) {
            lagMetrics.calculateAndRecordTopicRate(previousOffsets);
            clock.addAndGet(1_000);
        }

        var consumerRate = lagMetrics.calculateAndRecordTopicRate(topicEndOffsets);
        assertThat(consumerRate).isEqualTo(expectedTopicRate);
    }

    public static Stream<Arguments> calculateAndRecordTopicRate() {
        return Stream.of(
            Arguments.of(List.of(), OFFSETS_1, OptionalDouble.empty()),
            Arguments.of(List.of(OFFSETS_1), OFFSETS_1, OptionalDouble.empty()),
            Arguments.of(List.of(OFFSETS_1), OFFSETS_2, OptionalDouble.of(1D))
        );
    }
}

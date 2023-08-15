package com.brandwatch.kafka_pod_autoscaler.triggers.kafka;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TopicConsumerStatsTest {
    private static final Instant NOW = Instant.parse("2000-01-02T03:04:05.006Z");
    private static final TopicPartition PARTITION_1 = new TopicPartition("topic", 1);
    private static final TopicPartition PARTITION_2 = new TopicPartition("topic", 2);
    private static final TopicPartition PARTITION_3 = new TopicPartition("topic", 3);
    private static final TopicPartition PARTITION_4 = new TopicPartition("topic", 4);

    @ParameterizedTest
    @MethodSource
    public void variousScenarios(List<UpdateCallParameters> updateCalls,
                                 ExpectedResults expectedResults) {
        var clock = new AtomicLong(NOW.toEpochMilli());
        var stats = new TopicConsumerStats("topic", "test", clock::get);

        stats.setMinimumTopicRateMeasurements(0L);
        stats.setMinimumConsumerRateMeasurements(0L);
        stats.setConsumerCommitTimeout(Duration.ofSeconds(10L));

        for (var call : updateCalls) {
            stats.update(call.replicaCount, call.consumerOffsets, call.topicEndOffsets);
            clock.addAndGet(call.tickBy);
        }

        assertThat(stats.getLag(), equalTo(expectedResults.lag()));
        assertThat(stats.estimateConsumerRate(expectedResults.replicaCount()), equalTo(expectedResults.consumerRate()));
        assertThat(stats.getTopicRate(), equalTo(expectedResults.topicRate()));
    }

    public static Stream<Arguments> variousScenarios() {
        return Stream.of(
            Arguments.of(noCalls(), expect(1, 0, OptionalDouble.empty(), OptionalDouble.empty())),
            Arguments.of(oneCallSameOffsets(), expect(1, 0, OptionalDouble.empty(), OptionalDouble.empty())),
            Arguments.of(manyOfTheSameOffset_withinCommitTimeout(), expect(1, 0, OptionalDouble.empty(), OptionalDouble.of(0D))),
            Arguments.of(manyOfTheSameOffset_outsideCommitTimeout(), expect(1, 0, OptionalDouble.of(0D), OptionalDouble.of(0D))),
            Arguments.of(stuckConsumer(), expect(1, 8, OptionalDouble.of(0D), OptionalDouble.of(0.4D))),
            Arguments.of(slowConsumer(), expect(1, 24, OptionalDouble.of(2D), OptionalDouble.of(16D))),
            Arguments.of(keepingUpConsumer(), expect(1, 0L, OptionalDouble.of(8D), OptionalDouble.of(16D))),
            Arguments.of(keepingUpConsumer(), expect(2, 0L, OptionalDouble.of(16D), OptionalDouble.of(16D))),
            Arguments.of(catchingUpConsumer(), expect(1, 0, OptionalDouble.of(6D), OptionalDouble.of(4D))),
            Arguments.of(catchingUpConsumer(), expect(2, 0, OptionalDouble.of(12D), OptionalDouble.of(4D))),
            Arguments.of(scalingCatchingUpConsumer(), expect(2, 0, OptionalDouble.of(6D), OptionalDouble.of(8D)))
        );
    }

    private static List<UpdateCallParameters> noCalls() {
        return List.of();
    }

    private static List<UpdateCallParameters> oneCallSameOffsets() {
        return List.of(call(2, evenOffsets(0L), evenOffsets(0L)));
    }

    private static List<UpdateCallParameters> manyOfTheSameOffset_withinCommitTimeout() {
        return IntStream.range(0, 10)
            .mapToObj(i -> call(2, evenOffsets(0L), evenOffsets(0L)))
            .toList();
    }

    private static List<UpdateCallParameters> manyOfTheSameOffset_outsideCommitTimeout() {
        return IntStream.range(0, 1_000)
            .mapToObj(i -> call(2, evenOffsets(0L), evenOffsets(0L), 10_000))
            .toList();
    }

    private static List<UpdateCallParameters> stuckConsumer() {
        return List.of(
            call(2, evenOffsets(0L), evenOffsets(0L), 10_000),
            call(2, evenOffsets(0L), evenOffsets(1L), 10_000),
            call(2, evenOffsets(0L), evenOffsets(2L), 10_000)
        );
    }

    private static List<UpdateCallParameters> slowConsumer() {
        return List.of(
            call(2, evenOffsets(0L), evenOffsets(1L)),
            call(2, evenOffsets(1L), evenOffsets(4L)),
            call(2, evenOffsets(2L), evenOffsets(8L))
        );
    }

    private static List<UpdateCallParameters> keepingUpConsumer() {
        return List.of(
            call(2, evenOffsets(1L), evenOffsets(1L)),
            call(2, evenOffsets(4L), evenOffsets(4L)),
            call(2, evenOffsets(8L), evenOffsets(8L))
        );
    }

    private static List<UpdateCallParameters> catchingUpConsumer() {
        return List.of(
            call(2, evenOffsets(1L), evenOffsets(4L)),
            call(2, evenOffsets(3L), evenOffsets(5L)),
            call(2, evenOffsets(6L), evenOffsets(6L))
        );
    }

    private static List<UpdateCallParameters> scalingCatchingUpConsumer() {
        return List.of(
            call(2, evenOffsets(0L), evenOffsets(2L)),
            call(2, evenOffsets(1L), evenOffsets(4L)),
            call(4, evenOffsets(4L), evenOffsets(6L)),
            call(4, evenOffsets(7L), evenOffsets(8L)),
            call(4, evenOffsets(9L), evenOffsets(10L)),
            call(4, evenOffsets(12L), evenOffsets(12L))
        );
    }

    private static Map<TopicPartition, Long> evenOffsets(long offset) {
        return Map.of(
            PARTITION_1, offset,
            PARTITION_2, offset,
            PARTITION_3, offset,
            PARTITION_4, offset
        );
    }

    private static UpdateCallParameters call(int replicaCount,
                                             Map<TopicPartition, Long> consumerOffsets,
                                             Map<TopicPartition, Long> topicEndOffsets) {
        return new UpdateCallParameters(replicaCount, consumerOffsets, topicEndOffsets, 1_000);
    }

    private static UpdateCallParameters call(int replicaCount,
                                             Map<TopicPartition, Long> consumerOffsets,
                                             Map<TopicPartition, Long> topicEndOffsets,
                                             long tickBy) {
        return new UpdateCallParameters(replicaCount, consumerOffsets, topicEndOffsets, tickBy);
    }

    private static ExpectedResults expect(int replicaCount, long lag, OptionalDouble consumerRate, OptionalDouble topicRate) {
        return new ExpectedResults(replicaCount, lag, consumerRate, topicRate);
    }

    public record UpdateCallParameters(
        int replicaCount,
        Map<TopicPartition, Long> consumerOffsets,
        Map<TopicPartition, Long> topicEndOffsets,
        long tickBy) {
    }

    public record ExpectedResults(int replicaCount, long lag, OptionalDouble consumerRate, OptionalDouble topicRate) {
    }
}

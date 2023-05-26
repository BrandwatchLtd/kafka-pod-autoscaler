package com.brandwatch.kafka_pod_autoscaler.triggers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TriggerResultTest {
    @ParameterizedTest
    @MethodSource
    public void recommendedReplicas(double inputValue, double targetThreshold, int currentReplicaCount, int expectedReplicaCount) {
        var result = new TriggerResult(null, inputValue, targetThreshold).recommendedReplicas(currentReplicaCount);

        assertThat(result).isEqualTo(expectedReplicaCount);
    }

    public static Stream<Arguments> recommendedReplicas() {
        return Stream.of(
                Arguments.of(0, 10, 5, 0),
                Arguments.of(9, 100, 2, 1),
                Arguments.of(3, 1, 1, 3)
        );
    }
}

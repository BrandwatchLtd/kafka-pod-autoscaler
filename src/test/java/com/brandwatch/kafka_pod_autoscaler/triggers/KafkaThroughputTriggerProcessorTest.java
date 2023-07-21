package com.brandwatch.kafka_pod_autoscaler.triggers;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import brandwatch.com.v1alpha1.KafkaPodAutoscaler;
import brandwatch.com.v1alpha1.KafkaPodAutoscalerSpec;
import brandwatch.com.v1alpha1.kafkapodautoscalerspec.Triggers;
import io.fabric8.kubernetes.client.KubernetesClient;

import com.brandwatch.kafka_pod_autoscaler.ScaledResource;
import com.brandwatch.kafka_pod_autoscaler.cache.KafkaMetadata;
import com.brandwatch.kafka_pod_autoscaler.cache.KafkaMetadataCache;

@ExtendWith(MockitoExtension.class)
public class KafkaThroughputTriggerProcessorTest {
    private static final String BOOTSTRAP_SERVERS = "KafkaThroughputTriggerProcessorTest";
    private static final String TOPIC_NAME = "topic";
    private static final String CONSUMER_GROUP_ID = "consumer-group";
    private static final Instant NOW = Instant.parse("2000-01-02T03:04:05.0006Z");

    @Mock
    private KafkaMetadata kafkaMetadata;
    @Mock
    private KubernetesClient client;
    @Mock
    private ScaledResource resource;
    @Mock
    private KafkaPodAutoscaler autoscaler;
    @Mock
    private Triggers trigger;

    private final KafkaThroughputTriggerProcessor triggerProcessor = new KafkaThroughputTriggerProcessor();

    @BeforeEach
    public void before() {
        KafkaMetadataCache.put(BOOTSTRAP_SERVERS, kafkaMetadata);

        var spec = new KafkaPodAutoscalerSpec();
        spec.setTopicName(TOPIC_NAME);
        spec.setBootstrapServers(BOOTSTRAP_SERVERS);
        when(autoscaler.getSpec()).thenReturn(spec);

        when(trigger.getMetadata()).thenReturn(Map.of("consumerGroupId", CONSUMER_GROUP_ID));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    public void canCalculateRate(String name, List<TopicOffsets> topicOffsets, long expectedInputValue, long expectedTargetThresold)
            throws InterruptedException {
        var seconds = 0;

        TriggerResult triggerResult = null;
        for (var topicOffset : topicOffsets) {
            triggerProcessor.setClock(Clock.fixed(NOW.plusSeconds(seconds++), ZoneOffset.UTC));

            givenConsumerOffsets(topicOffset.consumerOffsets());
            givenTopicEndOffsets(topicOffset.topicEndOffsets());
            triggerResult = triggerProcessor.process(client, resource, autoscaler, trigger, 1);
        }
        assertThat(triggerResult.inputValue()).isEqualTo(expectedInputValue);
        assertThat(triggerResult.targetThreshold()).isEqualTo(expectedTargetThresold);
    }

    public static Stream<Arguments> canCalculateRate() {
        return Stream.of(
            Arguments.of(
                "All partitions consuming at same rate",
                List.of(
                    new TopicOffsets(new long[] { 5, 5, 5, 5 }, new long[] { 10, 10, 10, 10 }),
                    new TopicOffsets(new long[] { 10, 10, 10, 10 }, new long[] { 20, 20, 20, 20 })
                ),
                5L,
                10L
            ),
            Arguments.of(
                "Takes the rate of the slowest consuming partition",
                List.of(
                    new TopicOffsets(new long[] { 5, 5, 5, 5 }, new long[] { 10, 10, 10, 10 }),
                    new TopicOffsets(new long[] { 8, 7, 6, 9 }, new long[] { 20, 20, 20, 20 })
                ),
                1L,
                10L
            )
        );
    }

    private void givenConsumerOffsets(long... partitionValues) throws InterruptedException {
        var partitionOffsets = new HashMap<TopicPartition, Long>();
        for (var i = 0; i < partitionValues.length; i++) {
            var offset = partitionValues[i];

            partitionOffsets.put(new TopicPartition(TOPIC_NAME, i), offset);
        }

        when(kafkaMetadata.getConsumerOffsets(TOPIC_NAME, CONSUMER_GROUP_ID))
            .thenReturn(partitionOffsets);
    }

    private void givenTopicEndOffsets(long... partitionValues) throws InterruptedException {
        var partitionOffsets = new HashMap<TopicPartition, Long>();
        for (var i = 0; i < partitionValues.length; i++) {
            var offset = partitionValues[i];

            partitionOffsets.put(new TopicPartition(TOPIC_NAME, i), offset);
        }

        when(kafkaMetadata.getTopicEndOffsets(TOPIC_NAME))
            .thenReturn(partitionOffsets);
    }

    private record TopicOffsets(long[] consumerOffsets, long[] topicEndOffsets) {
    }
}

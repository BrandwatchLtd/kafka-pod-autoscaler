package com.brandwatch.kafka_pod_autoscaler.triggers;

import static java.util.Objects.requireNonNull;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.auto.service.AutoService;

import brandwatch.com.v1alpha1.KafkaPodAutoscaler;
import brandwatch.com.v1alpha1.kafkapodautoscalerspec.Triggers;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.ScaledResource;
import com.brandwatch.kafka_pod_autoscaler.cache.KafkaMetadataCache;

@Slf4j
@AutoService(TriggerProcessor.class)
public class KafkaThroughputTriggerProcessor implements TriggerProcessor {
    private static final LoadingCache<TopicConsumerGroupId, Cache<PartitionTimestamp, Long>> consumerOffsetHistory = Caffeine
        .newBuilder()
        .expireAfterAccess(Duration.ofMinutes(5L))
        .build(key -> Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10L))
            .build());
    private static final LoadingCache<TopicConsumerGroupId, Cache<PartitionTimestamp, Long>> topicEndHistory = Caffeine
        .newBuilder()
        .expireAfterAccess(Duration.ofMinutes(5L))
        .build(key -> Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10L))
            .build());

    @Setter // (onMethod_ = { @VisibleForTesting })
    private Clock clock = Clock.systemUTC();

    @Override
    public String getType() {
        return "kafkaThroughput";
    }

    @Override
    public TriggerResult process(KubernetesClient client, ScaledResource resource, KafkaPodAutoscaler autoscaler, Triggers trigger, int replicaCount) {
        var topic = autoscaler.getSpec().getTopicName();
        var bootstrapServers = autoscaler.getSpec().getBootstrapServers();
        var consumerGroupId = requireNonNull(trigger.getMetadata().get("consumerGroupId"));
        var topicConsumerGroupId = new TopicConsumerGroupId(topic, consumerGroupId);

        logger.debug("Requesting kafka metrics for topic={} and consumerGroupId={}", topic, consumerGroupId);

        var adminApi = KafkaMetadataCache.get(bootstrapServers);
        try {
            var timestamp = clock.millis();
            var consumerOffsets = adminApi.getConsumerOffsets(topic, consumerGroupId);
            var historicConsumerOffsets = consumerOffsetHistory.get(topicConsumerGroupId);
            historicConsumerOffsets.putAll(consumerOffsets.entrySet().stream()
                 .collect(Collectors.toMap(e -> new PartitionTimestamp(e.getKey().partition(), timestamp),
                                           Map.Entry::getValue)));
            var consumerRate = calculateRate(historicConsumerOffsets);

            var topicEndOffsets = adminApi.getTopicEndOffsets(topic);
            var historicTopicEndOffsets = topicEndHistory.get(topicConsumerGroupId);
            historicTopicEndOffsets.putAll(topicEndOffsets.entrySet().stream()
                .collect(Collectors.toMap(e -> new PartitionTimestamp(e.getKey().partition(), timestamp),
                                          Map.Entry::getValue)));
            var producerRate = calculateRate(historicTopicEndOffsets);

            return new TriggerResult(trigger, consumerRate, producerRate);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (RuntimeException e) {
            KafkaMetadataCache.remove(bootstrapServers);
            throw e;
        }
    }

    private long calculateRate(Cache<PartitionTimestamp, Long> offsets) {
        var timestamps = offsets.asMap().keySet().stream()
            .mapToLong(aLong -> aLong.timestamp)
            .sorted()
            .distinct()
            .toArray();

        if (timestamps.length < 2) {
            return 0;
        }

        var earliestTimestamp = timestamps[0];
        var latestTimestamp = timestamps[timestamps.length - 1];

        var timestampDelta = latestTimestamp - earliestTimestamp;
        if (timestampDelta == 0) {
            return 0;
        }

        // Find the slowest-consuming partition
        var earliestOffsets = offsets.asMap().entrySet().stream()
            .filter(off -> off.getKey().timestamp() == earliestTimestamp)
            .collect(Collectors.toMap(off -> off.getKey().partition, off -> off.getValue()));
        var latestOffsets = offsets.asMap().entrySet().stream()
            .filter(off -> off.getKey().timestamp() == latestTimestamp)
            .collect(Collectors.toMap(off -> off.getKey().partition, off -> off.getValue()));

        var smallestOffsetDelta = Long.MAX_VALUE;
        for (var partition : latestOffsets.keySet()) {
            if (!earliestOffsets.containsKey(partition)) {
                continue;
            }
            var partitionOffsetDelta = (latestOffsets.get(partition) - earliestOffsets.get(partition));
            if (partitionOffsetDelta < smallestOffsetDelta) {
                smallestOffsetDelta = partitionOffsetDelta;
            }
        }

        // Return the consumption rate in ops/sec
        return smallestOffsetDelta / (timestampDelta / 1000);
    }

    private record TopicConsumerGroupId(String topic, String consumerGroupId) {
    }

    private record PartitionTimestamp(int partition, long timestamp) {
    }
}

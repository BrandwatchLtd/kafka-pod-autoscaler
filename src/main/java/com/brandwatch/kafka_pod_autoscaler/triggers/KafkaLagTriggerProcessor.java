package com.brandwatch.kafka_pod_autoscaler.triggers;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.auto.service.AutoService;

import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.ScaledResource;
import com.brandwatch.kafka_pod_autoscaler.cache.KafkaMetadataCache;
import com.brandwatch.kafka_pod_autoscaler.triggers.kafka.LagMetrics;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.KafkaPodAutoscaler;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerspec.TriggerDefinition;

@Slf4j
@AutoService(TriggerProcessor.class)
public class KafkaLagTriggerProcessor implements TriggerProcessor {
    private static final LoadingCache<TopicConsumerGroupId, LagMetrics> lagMetricsCache = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(10))
        .build(id -> new LagMetrics());

    @Override
    public String getType() {
        return "kafka";
    }

    @Override
    public TriggerResult process(KubernetesClient client, ScaledResource resource, KafkaPodAutoscaler autoscaler, TriggerDefinition trigger, int replicaCount) {
        var topic = autoscaler.getSpec().getTopicName();
        var bootstrapServers = autoscaler.getSpec().getBootstrapServers();
        var consumerGroupId = requireNonNull(trigger.getMetadata().get("consumerGroupId"));
        var threshold = Integer.parseInt(requireNonNull(trigger.getMetadata().get("threshold")));
        var sla = Duration.parse(Optional.ofNullable(trigger.getMetadata().get("sla")).orElse("PT10M"));

        logger.debug("Requesting kafka metrics for topic={} and consumerGroupId={}", topic, consumerGroupId);

        var lagMetrics = lagMetricsCache.get(new TopicConsumerGroupId(topic, consumerGroupId));
        var kafkaMetadata = KafkaMetadataCache.get(bootstrapServers);
        try {
            var consumerOffsets = kafkaMetadata.getConsumerOffsets(topic, consumerGroupId);
            var topicEndOffsets = kafkaMetadata.getTopicEndOffsets(topic);

            var lag = consumerOffsets.keySet().stream()
                .mapToLong(partition -> topicEndOffsets.get(partition) - consumerOffsets.get(partition))
                .sum();

            double consumerRate;
            var targetRate = lagMetrics.calculateAndRecordTopicRate(topicEndOffsets).orElse(0);
            if (lag < threshold) {
                // ensure the consumers are fast enough to keep up and not start lagging, at this rate
                consumerRate = lagMetrics.estimateLoadedConsumerRate(replicaCount, consumerOffsets).orElse(targetRate);

                // Invert the TriggerResult
                // We're usually dealing in scaling _up_ until the values meet, but in this case we want to scale _down_
                return TriggerResult.inverted(trigger, consumerRate, targetRate);
            } else {
                consumerRate = lagMetrics.calculateConsumerRate(replicaCount, consumerOffsets).orElse(0);
                // Record this consumer rate as a rate-under-load, so we can use it to calculate the ideal replica count when not lagged
                lagMetrics.recordConsumerRate(replicaCount, consumerOffsets);

                // We need to catch up, so calculate a target rate that will clear the lag within the SLA
                var rateRequiredToClearLag = lag / (double) sla.toSeconds();
                targetRate = targetRate + rateRequiredToClearLag;

                return new TriggerResult(trigger, consumerRate, targetRate);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (RuntimeException e) {
            KafkaMetadataCache.remove(bootstrapServers);
            throw e;
        }
    }

    private record TopicConsumerGroupId(String topic, String consumerGroupId) {
    }
}

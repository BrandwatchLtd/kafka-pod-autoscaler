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
import com.brandwatch.kafka_pod_autoscaler.triggers.kafka.TopicConsumerStats;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.KafkaPodAutoscaler;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerspec.TriggerDefinition;

@Slf4j
@AutoService(TriggerProcessor.class)
public class KafkaLagTriggerProcessor implements TriggerProcessor {
    private static final LoadingCache<TopicConsumerGroupId, TopicConsumerStats> lagModelCache = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(10))
        .build(id -> new TopicConsumerStats());

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

        var lagModel = lagModelCache.get(new TopicConsumerGroupId(topic, consumerGroupId));
        var kafkaMetadata = KafkaMetadataCache.get(bootstrapServers);
        try {
            var consumerOffsets = kafkaMetadata.getConsumerOffsets(topic, consumerGroupId);
            var topicEndOffsets = kafkaMetadata.getTopicEndOffsets(topic);

            lagModel.update(replicaCount, consumerOffsets, topicEndOffsets);

            var lag = lagModel.getLag();
            var targetRate = lagModel.getTopicRate().orElse(0);
            var consumerRate = lagModel.estimateConsumerRate(replicaCount).orElse(targetRate);
            if (lag > threshold) {
                // We need to catch up, so calculate a target rate that will clear the lag within the SLA
                var rateRequiredToClearLag = lag / (double) sla.toSeconds();
                targetRate = targetRate + rateRequiredToClearLag;
            }
            // We need to invert the calculation because we need to scale _up_ to the target rate, not down
            return TriggerResult.inverted(trigger, consumerRate, targetRate);
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

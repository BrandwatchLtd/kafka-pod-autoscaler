package com.brandwatch.kafka_pod_autoscaler.triggers;

import static java.util.Objects.requireNonNull;

import com.google.auto.service.AutoService;

import brandwatch.com.v1alpha1.KafkaPodAutoscaler;
import brandwatch.com.v1alpha1.kafkapodautoscalerspec.Triggers;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.ScaledResource;
import com.brandwatch.kafka_pod_autoscaler.cache.KafkaMetadataCache;

@Slf4j
@AutoService(TriggerProcessor.class)
public class KafkaLagTriggerProcessor implements TriggerProcessor {
    @Override
    public String getType() {
        return "kafka";
    }

    @Override
    public TriggerResult process(KubernetesClient client, ScaledResource resource, KafkaPodAutoscaler autoscaler, Triggers trigger, int replicaCount) {
        var topic = autoscaler.getSpec().getTopicName();
        var bootstrapServers = autoscaler.getSpec().getBootstrapServers();
        var consumerGroupId = requireNonNull(trigger.getMetadata().get("consumerGroupId"));
        var threshold = Integer.parseInt(requireNonNull(trigger.getMetadata().get("threshold")));

        logger.debug("Requesting kafka metrics for topic={} and consumerGroupId={}", topic, consumerGroupId);

        var kafkaMetadata = KafkaMetadataCache.get(bootstrapServers);
        try {
            var consumerOffsets = kafkaMetadata.getConsumerOffsets(topic, consumerGroupId);
            var topicEndOffsets = kafkaMetadata.getTopicEndOffsets(topic);

            var lag = consumerOffsets.keySet().stream()
                                     .mapToLong(partition -> topicEndOffsets.get(partition) - consumerOffsets.get(partition))
                                     .sum();

            return new TriggerResult(trigger, lag, threshold);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (RuntimeException e) {
            KafkaMetadataCache.remove(bootstrapServers);
            throw e;
        }
    }
}

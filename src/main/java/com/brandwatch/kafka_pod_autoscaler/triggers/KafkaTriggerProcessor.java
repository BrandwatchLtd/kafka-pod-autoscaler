package com.brandwatch.kafka_pod_autoscaler.triggers;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;

import com.google.auto.service.AutoService;

import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.ScaledResource;
import com.brandwatch.kafka_pod_autoscaler.cache.AdminClientCache;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.KafkaPodAutoscaler;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerspec.Triggers;

@Slf4j
@AutoService(TriggerProcessor.class)
public class KafkaTriggerProcessor implements TriggerProcessor {
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

        var adminApi = AdminClientCache.get(bootstrapServers);
        try {
            var partitions = adminApi.describeTopics(List.of(topic)).topicNameValues().get(topic).get().partitions();
            var latestOffsetRequests = partitions
                    .stream()
                    .collect(Collectors.toMap(p -> new TopicPartition(topic, p.partition()), p -> OffsetSpec.latest()));

            var consumerOffsets = getConsumerOffsets(adminApi, topic, consumerGroupId);
            var topicEndOffsets = adminApi.listOffsets(latestOffsetRequests).all().get().entrySet()
                                          .stream()
                                          .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().offset()));

            var lag = consumerOffsets.keySet().stream()
                                     .mapToLong(partition -> topicEndOffsets.get(partition) - consumerOffsets.get(partition))
                                     .sum();

            return new TriggerResult(trigger, lag, threshold);
        } catch (ExecutionException e) {
            AdminClientCache.remove(bootstrapServers);
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static Map<TopicPartition, Long> getConsumerOffsets(AdminClient adminApi, String topic, String consumerGroupId)
            throws InterruptedException {
        try {
            return adminApi.listConsumerGroupOffsets(consumerGroupId)
                           .partitionsToOffsetAndMetadata().get()
                           .entrySet().stream()
                           .filter(tp -> tp.getKey().topic().equals(topic))
                           .collect(Collectors.toMap(Map.Entry::getKey, m -> m.getValue().offset()));
        } catch (ExecutionException ignored) {
            // ignore, starting up
            Thread.sleep(100);
            return Map.of();
        }
    }
}

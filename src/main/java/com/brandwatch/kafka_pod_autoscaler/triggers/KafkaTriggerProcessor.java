package com.brandwatch.kafka_pod_autoscaler.triggers;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;

import com.google.auto.service.AutoService;

import brandwatch.com.v1alpha1.KafkaPodAutoscaler;
import brandwatch.com.v1alpha1.kafkapodautoscalerspec.Triggers;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.ScaledResource;

@Slf4j
@AutoService(TriggerProcessor.class)
public class KafkaTriggerProcessor implements TriggerProcessor {
    @Override
    public String getType() {
        return "kafka";
    }

    @Override
    public TriggerResult process(KubernetesClient client, ScaledResource resource, KafkaPodAutoscaler autoscaler, Triggers trigger, int currentReplicaCount) {
        var topic = autoscaler.getSpec().getTopicName();
        var bootstrapServers = autoscaler.getSpec().getBootstrapServers();
        var consumerGroupId = requireNonNull(trigger.getMetadata().get("consumerGroupId"));
        var threshold = Integer.parseInt(requireNonNull(trigger.getMetadata().get("threshold")));

        logger.info("Requesting kafka metrics for topic={} and consumerGroupId={}", topic, consumerGroupId);

        var properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (var adminApi = KafkaAdminClient.create(properties)) {
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

            var newReplicas = (int) Math.ceil(currentReplicaCount * (lag / (double) threshold));
            return new TriggerResult(trigger, newReplicas);
        } catch (ExecutionException e) {
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

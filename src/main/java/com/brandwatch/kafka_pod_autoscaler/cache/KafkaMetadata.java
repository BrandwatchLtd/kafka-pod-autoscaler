package com.brandwatch.kafka_pod_autoscaler.cache;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;

public class KafkaMetadata {
    private final AdminClient adminClient;

    public KafkaMetadata(AdminClient adminClient) {
        this.adminClient = adminClient;
    }

    public Map<TopicPartition, Long> getTopicEndOffsets(String topic) throws InterruptedException {
        try {
            var partitions = adminClient.describeTopics(List.of(topic)).topicNameValues().get(topic).get().partitions();
            var latestOffsetRequests = partitions
                .stream()
                .collect(Collectors.toMap(p -> new TopicPartition(topic, p.partition()), p -> OffsetSpec.latest()));
            return adminClient.listOffsets(latestOffsetRequests).all().get().entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().offset()));
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<TopicPartition, Long> getConsumerOffsets(String topic, String consumerGroupId)
        throws InterruptedException {
        try {
            return adminClient.listConsumerGroupOffsets(consumerGroupId)
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

    public int countPartitions(String topic) {
        try {
            return adminClient.describeTopics(List.of(topic)).values().get(topic).get().partitions().size();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        adminClient.close();
    }
}

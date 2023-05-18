package com.brandwatch.kafka_pod_autoscaler;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PartitionCountFetcher {
    public int countPartitions(@NonNull String bootstrapServers, @NonNull String topic) {
        logger.info("Requesting kafka metrics for bootstrapServers={} and topic={}", bootstrapServers, topic);

        var properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (var adminApi = KafkaAdminClient.create(properties)) {
            return adminApi.describeTopics(List.of(topic)).values().get(topic).get().partitions().size();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}

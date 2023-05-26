package com.brandwatch.kafka_pod_autoscaler;

import java.util.List;
import java.util.concurrent.ExecutionException;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.cache.AdminClientCache;

@Slf4j
public class PartitionCountFetcher {
    public int countPartitions(@NonNull String bootstrapServers, @NonNull String topic) {
        logger.debug("Requesting kafka metrics for bootstrapServers={} and topic={}", bootstrapServers, topic);

        var adminApi = AdminClientCache.get(bootstrapServers);
        try {
            return adminApi.describeTopics(List.of(topic)).values().get(topic).get().partitions().size();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}

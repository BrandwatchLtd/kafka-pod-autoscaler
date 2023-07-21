package com.brandwatch.kafka_pod_autoscaler;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.cache.KafkaMetadataCache;

@Slf4j
public class PartitionCountFetcher {
    public int countPartitions(@NonNull String bootstrapServers, @NonNull String topic) {
        logger.debug("Requesting kafka metrics for bootstrapServers={} and topic={}", bootstrapServers, topic);

        var kafkaMetadata = KafkaMetadataCache.get(bootstrapServers);
        return kafkaMetadata.countPartitions(topic);
    }
}

package com.brandwatch.kafka_pod_autoscaler.cache;

import java.time.Duration;
import java.util.Properties;

import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.google.common.annotations.VisibleForTesting;

public class KafkaMetadataCache {
    private static final Cache<String, KafkaMetadata> adminClientCache = Caffeine.newBuilder()
           .expireAfterAccess(Duration.ofMinutes(10))
           .removalListener((RemovalListener<String, KafkaMetadata>) (key, value, cause) -> {
               if (value != null) {
                   value.close();
               }
           })
           .build();

    public static KafkaMetadata get(String bootstrapServers) {
        return adminClientCache.get(bootstrapServers, s -> {
            var properties = new Properties();
            properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            return new KafkaMetadata(KafkaAdminClient.create(properties));
        });
    }

    public static void remove(String bootstrapServers) {
        adminClientCache.invalidate(bootstrapServers);
    }

    @VisibleForTesting
    public static void put(String bootstrapServers, KafkaMetadata kafkaMetadata) {
        adminClientCache.put(bootstrapServers, kafkaMetadata);
    }
}

package com.brandwatch.kafka_pod_autoscaler.cache;

import java.time.Duration;
import java.util.Properties;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;

public class AdminClientCache {
    private static final Cache<String, AdminClient> adminClientCache = Caffeine.newBuilder()
           .expireAfterAccess(Duration.ofMinutes(10))
           .removalListener((RemovalListener<String, AdminClient>) (key, value, cause) -> {
               if (value != null) {
                   value.close();
               }
           })
           .build();

    public static AdminClient get(String bootstrapServers) {
        return adminClientCache.get(bootstrapServers, s -> {
            var properties = new Properties();
            properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            return KafkaAdminClient.create(properties);
        });
    }
}

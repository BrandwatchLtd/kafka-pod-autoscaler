package com.brandwatch.kafka_pod_autoscaler;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;

import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;
import io.javaoperatorsdk.operator.monitoring.micrometer.MicrometerMetrics;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.CollectorRegistry;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.handlers.LivenessHandler;
import com.brandwatch.kafka_pod_autoscaler.handlers.MetricsHandler;
import com.brandwatch.kafka_pod_autoscaler.handlers.StartupHandler;

@Slf4j
public class Main {
    public static void main(String... args) throws IOException {
        logger.info("Starting operator");

        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, CollectorRegistry.defaultRegistry, Clock.SYSTEM);
        Metrics.globalRegistry.add(registry);
        var leaderElectionConfiguration = new LeaderElectionConfiguration("kafka-pod-autoscaler");
        var client = new KubernetesClientBuilder().build();
        Operator operator = new Operator(c -> c
                .withKubernetesClient(client)
                .withLeaderElectionConfiguration(leaderElectionConfiguration)
                .withMetrics(MicrometerMetrics.withoutPerResourceMetrics(Metrics.globalRegistry))
        );

        operator.register(new KafkaPodAutoscalerReconciler(new PartitionCountFetcher()));
        operator.start();

        var server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8080), 0);
        server.createContext("/startup", new StartupHandler(operator));
        server.createContext("/healthz", new LivenessHandler(operator));
        server.createContext("/metricz", new MetricsHandler(registry));
        server.setExecutor(null);
        server.start();
    }
}

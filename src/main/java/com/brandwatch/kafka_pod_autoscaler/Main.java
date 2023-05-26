package com.brandwatch.kafka_pod_autoscaler;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;

import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.handlers.LivenessHandler;
import com.brandwatch.kafka_pod_autoscaler.handlers.StartupHandler;

@Slf4j
public class Main {
    public static void main(String... args) throws IOException {
        logger.info("Starting operator");

        LeaderElectionConfiguration leaderElectionConfiguration = new LeaderElectionConfiguration("kafka-pod-autoscaler");

        var client = new KubernetesClientBuilder().build();
        Operator operator = new Operator(client, c -> c.withLeaderElectionConfiguration(leaderElectionConfiguration));

        operator.register(new KafkaPodAutoscalerReconciler(new PartitionCountFetcher()));
        operator.start();

        var server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8080), 0);
        server.createContext("/startup", new StartupHandler(operator));
        server.createContext("/healthz", new LivenessHandler(operator));
        server.setExecutor(null);
        server.start();
    }
}

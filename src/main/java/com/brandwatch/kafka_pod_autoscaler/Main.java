package com.brandwatch.kafka_pod_autoscaler;

import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {
    public static void main(String... args) {
        logger.info("Starting operator");

        LeaderElectionConfiguration leaderElectionConfiguration = new LeaderElectionConfiguration("kafka-pod-autoscaler");

        var client = new KubernetesClientBuilder().build();
        Operator operator = new Operator(client, c -> c.withLeaderElectionConfiguration(leaderElectionConfiguration));

        operator.register(new KafkaPodAutoscalerReconciler(true));
        operator.start();
    }
}

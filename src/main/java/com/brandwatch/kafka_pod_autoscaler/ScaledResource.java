package com.brandwatch.kafka_pod_autoscaler;

public interface ScaledResource {
    boolean isReady();

    int getReplicaCount();

    void scale(int bestReplicaCount);
}

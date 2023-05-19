package com.brandwatch.kafka_pod_autoscaler;

import java.util.List;

import io.fabric8.kubernetes.api.model.Pod;

public interface ScaledResource {
    boolean isReady();

    int getReplicaCount();

    void scale(int bestReplicaCount);

    List<Pod> pods();
}

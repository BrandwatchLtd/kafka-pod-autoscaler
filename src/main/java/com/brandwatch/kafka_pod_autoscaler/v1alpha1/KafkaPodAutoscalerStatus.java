package com.brandwatch.kafka_pod_autoscaler.v1alpha1;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.client.utils.Serialization;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerstatus.TriggerResults;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"calculatedReplicaCount","currentReplicaCount","dryRunReplicas","finalReplicaCount","lastScale","message","partitionCount","timestamp","triggerResults"})
@JsonDeserialize
public class KafkaPodAutoscalerStatus implements KubernetesResource {

    @JsonProperty("calculatedReplicaCount")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer calculatedReplicaCount;

    public Integer getCalculatedReplicaCount() {
        return calculatedReplicaCount;
    }

    public void setCalculatedReplicaCount(Integer calculatedReplicaCount) {
        this.calculatedReplicaCount = calculatedReplicaCount;
    }

    @JsonProperty("currentReplicaCount")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer currentReplicaCount;

    public Integer getCurrentReplicaCount() {
        return currentReplicaCount;
    }

    public void setCurrentReplicaCount(Integer currentReplicaCount) {
        this.currentReplicaCount = currentReplicaCount;
    }

    @JsonProperty("dryRunReplicas")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer dryRunReplicas;

    public Integer getDryRunReplicas() {
        return dryRunReplicas;
    }

    public void setDryRunReplicas(Integer dryRunReplicas) {
        this.dryRunReplicas = dryRunReplicas;
    }

    @JsonProperty("finalReplicaCount")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer finalReplicaCount;

    public Integer getFinalReplicaCount() {
        return finalReplicaCount;
    }

    public void setFinalReplicaCount(Integer finalReplicaCount) {
        this.finalReplicaCount = finalReplicaCount;
    }

    /**
     * The date and time that this message was added
     */
    @JsonProperty("lastScale")
    @JsonPropertyDescription("The date and time that this message was added")
    @JsonSetter(nulls = Nulls.SKIP)
    private String lastScale;

    public String getLastScale() {
        return lastScale;
    }

    public void setLastScale(String lastScale) {
        this.lastScale = lastScale;
    }

    @JsonProperty("message")
    @JsonSetter(nulls = Nulls.SKIP)
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @JsonProperty("partitionCount")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer partitionCount;

    public Integer getPartitionCount() {
        return partitionCount;
    }

    public void setPartitionCount(Integer partitionCount) {
        this.partitionCount = partitionCount;
    }

    /**
     * The date and time that this message was added
     */
    @JsonProperty("timestamp")
    @JsonPropertyDescription("The date and time that this message was added")
    @JsonSetter(nulls = Nulls.SKIP)
    private String timestamp;

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    @JsonProperty("triggerResults")
    @JsonSetter(nulls = Nulls.SKIP)
    private List<TriggerResults> triggerResults = Serialization.unmarshal("[]", List.class);

    public List<TriggerResults> getTriggerResults() {
        return triggerResults;
    }

    public void setTriggerResults(List<TriggerResults> triggerResults) {
        this.triggerResults = triggerResults;
    }
}

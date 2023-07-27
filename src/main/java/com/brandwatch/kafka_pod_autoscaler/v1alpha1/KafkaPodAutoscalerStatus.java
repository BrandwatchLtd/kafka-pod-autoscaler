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
import io.fabric8.kubernetes.model.annotation.PrinterColumn;
import lombok.Getter;
import lombok.Setter;

import com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerstatus.TriggerResult;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"calculatedReplicaCount","currentReplicaCount","dryRunReplicas","finalReplicaCount","lastScale","message","partitionCount","timestamp","triggerResults"})
@JsonDeserialize
public class KafkaPodAutoscalerStatus implements KubernetesResource {

    @Getter
    @Setter
    @JsonProperty("calculatedReplicaCount")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer calculatedReplicaCount;
    @Getter
    @Setter
    @JsonProperty("currentReplicaCount")
    @JsonSetter(nulls = Nulls.SKIP)
    @PrinterColumn(name = "Replicas")
    private Integer currentReplicaCount;
    @Getter
    @Setter
    @JsonProperty("dryRunReplicas")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer dryRunReplicas;
    @Getter
    @Setter
    @JsonProperty("finalReplicaCount")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer finalReplicaCount;
    /**
     * The date and time that this message was added
     */
    @Getter
    @Setter
    @JsonProperty("lastScale")
    @JsonPropertyDescription("The date and time that this message was added")
    @JsonSetter(nulls = Nulls.SKIP)
    @PrinterColumn(name = "Last Scale")
    private String lastScale;
    @Getter
    @Setter
    @JsonProperty("message")
    @JsonSetter(nulls = Nulls.SKIP)
    @PrinterColumn(name = "Message")
    private String message;
    @Getter
    @Setter
    @JsonProperty("partitionCount")
    @JsonSetter(nulls = Nulls.SKIP)
    @PrinterColumn(name = "Partition Count")
    private Integer partitionCount;
    /**
     * The date and time that this message was added
     */
    @Getter
    @Setter
    @JsonProperty("timestamp")
    @JsonPropertyDescription("The date and time that this message was added")
    @JsonSetter(nulls = Nulls.SKIP)
    private String timestamp;
    @Getter
    @Setter
    @JsonProperty("triggerResults")
    @JsonSetter(nulls = Nulls.SKIP)
    private List<TriggerResult> triggerResults = Serialization.unmarshal("[]", List.class);
}

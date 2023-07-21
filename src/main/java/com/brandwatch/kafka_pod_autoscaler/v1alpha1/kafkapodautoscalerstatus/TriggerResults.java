package com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerstatus;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.fabric8.kubernetes.api.model.KubernetesResource;
import lombok.Getter;
import lombok.Setter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"inputValue","recommendedReplicas","targetThreshold","type"})
@JsonDeserialize
public class TriggerResults implements KubernetesResource {

    @Getter
    @Setter
    @JsonProperty("inputValue")
    @JsonSetter(nulls = Nulls.SKIP)
    private Long inputValue;
    @Getter
    @Setter
    @JsonProperty("recommendedReplicas")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer recommendedReplicas;
    @Getter
    @Setter
    @JsonProperty("targetThreshold")
    @JsonSetter(nulls = Nulls.SKIP)
    private Long targetThreshold;
    @Getter
    @Setter
    @JsonProperty("type")
    @JsonSetter(nulls = Nulls.SKIP)
    private String type;
}

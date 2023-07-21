package com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerstatus;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.fabric8.kubernetes.api.model.KubernetesResource;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"inputValue","recommendedReplicas","targetThreshold","type"})
@JsonDeserialize
public class TriggerResults implements KubernetesResource {

    @JsonProperty("inputValue")
    @JsonSetter(nulls = Nulls.SKIP)
    private Long inputValue;

    public Long getInputValue() {
        return inputValue;
    }

    public void setInputValue(Long inputValue) {
        this.inputValue = inputValue;
    }

    @JsonProperty("recommendedReplicas")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer recommendedReplicas;

    public Integer getRecommendedReplicas() {
        return recommendedReplicas;
    }

    public void setRecommendedReplicas(Integer recommendedReplicas) {
        this.recommendedReplicas = recommendedReplicas;
    }

    @JsonProperty("targetThreshold")
    @JsonSetter(nulls = Nulls.SKIP)
    private Long targetThreshold;

    public Long getTargetThreshold() {
        return targetThreshold;
    }

    public void setTargetThreshold(Long targetThreshold) {
        this.targetThreshold = targetThreshold;
    }

    @JsonProperty("type")
    @JsonSetter(nulls = Nulls.SKIP)
    private String type;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}

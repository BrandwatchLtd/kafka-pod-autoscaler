package com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerspec;

import java.util.Map;

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
@JsonPropertyOrder({"metadata","type"})
@JsonDeserialize
public class Triggers implements KubernetesResource {
    @Getter
    @Setter
    @JsonProperty("metadata")
    @JsonSetter(nulls = Nulls.SKIP)
    private Map<String, String> metadata;
    @Getter
    @Setter
    @JsonProperty("type")
    @JsonSetter(nulls = Nulls.SKIP)
    private String type;
}

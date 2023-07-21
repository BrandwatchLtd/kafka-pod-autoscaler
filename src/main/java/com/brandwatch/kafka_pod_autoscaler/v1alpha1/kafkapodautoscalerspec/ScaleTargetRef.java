package com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerspec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.fabric8.generator.annotation.Required;
import io.fabric8.kubernetes.api.model.KubernetesResource;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"apiVersion","kind","name"})
@JsonDeserialize
public class ScaleTargetRef implements KubernetesResource {

    @JsonProperty("apiVersion")
    @Required
    @JsonSetter(nulls = Nulls.SKIP)
    private String apiVersion;

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    @JsonProperty("kind")
    @Required
    @JsonSetter(nulls = Nulls.SKIP)
    private String kind = "Deployment";

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    @JsonProperty("name")
    @Required
    @JsonSetter(nulls = Nulls.SKIP)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

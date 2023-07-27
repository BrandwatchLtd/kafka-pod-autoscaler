package com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerspec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.fabric8.generator.annotation.Required;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.model.annotation.PrinterColumn;
import lombok.Getter;
import lombok.Setter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"apiVersion","kind","name"})
@JsonDeserialize
public class ScaleTargetRef implements KubernetesResource {
    @Getter
    @Setter
    @JsonProperty("apiVersion")
    @Required
    @JsonSetter(nulls = Nulls.SKIP)
    private String apiVersion;
    @Getter
    @Setter
    @JsonProperty("kind")
    @Required
    @JsonSetter(nulls = Nulls.SKIP)
    @PrinterColumn(name = "Target Kind")
    private String kind = "Deployment";
    @Getter
    @Setter
    @JsonProperty("name")
    @Required
    @JsonSetter(nulls = Nulls.SKIP)
    @PrinterColumn(name = "Target Name")
    private String name;
}

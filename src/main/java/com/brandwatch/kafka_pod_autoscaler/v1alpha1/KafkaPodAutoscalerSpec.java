package com.brandwatch.kafka_pod_autoscaler.v1alpha1;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.fabric8.generator.annotation.Required;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.Getter;
import lombok.Setter;

import com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerspec.ScaleTargetRef;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerspec.TriggerDefinition;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"bootstrapServers","cooloffSeconds","dryRun","scaleTargetRef","topicName","triggers"})
@JsonDeserialize
public class KafkaPodAutoscalerSpec implements KubernetesResource {

    @Getter
    @Setter
    @JsonProperty("bootstrapServers")
    @JsonSetter(nulls = Nulls.SKIP)
    private String bootstrapServers;
    @Getter
    @Setter
    @JsonProperty("cooloffSeconds")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer cooloffSeconds = 300;
    @Getter
    @Setter
    @JsonProperty("dryRun")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean dryRun = false;
    @Getter
    @Setter
    @JsonProperty("scaleTargetRef")
    @Required
    @JsonSetter(nulls = Nulls.SKIP)
    private ScaleTargetRef scaleTargetRef;
    @Getter
    @Setter
    @JsonProperty("topicName")
    @JsonSetter(nulls = Nulls.SKIP)
    private String topicName;
    @Getter
    @Setter
    @JsonProperty("triggers")
    @Required
    @JsonSetter(nulls = Nulls.SKIP)
    private List<TriggerDefinition> triggers = Serialization.unmarshal("[]", List.class);
    @Getter
    @Setter
    @JsonProperty("minReplicas")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer minReplicas = null;
    @Getter
    @Setter
    @JsonProperty("maxReplicas")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer maxReplicas = null;
    @Getter
    @Setter
    @JsonProperty("maxScaleIncrements")
    @JsonSetter(nulls = Nulls.SKIP)
    private int maxScaleIncrements = 1;
}

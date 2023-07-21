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
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerspec.ScaleTargetRef;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerspec.Triggers;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"bootstrapServers","cooloffSeconds","dryRun","scaleTargetRef","topicName","triggers"})
@JsonDeserialize
public class KafkaPodAutoscalerSpec implements KubernetesResource {

    @JsonProperty("bootstrapServers")
    @JsonSetter(nulls = Nulls.SKIP)
    private String bootstrapServers;

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @JsonProperty("cooloffSeconds")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer cooloffSeconds = 300;

    public Integer getCooloffSeconds() {
        return cooloffSeconds;
    }

    public void setCooloffSeconds(Integer cooloffSeconds) {
        this.cooloffSeconds = cooloffSeconds;
    }

    @JsonProperty("dryRun")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean dryRun = false;

    public Boolean getDryRun() {
        return dryRun;
    }

    public void setDryRun(Boolean dryRun) {
        this.dryRun = dryRun;
    }

    @JsonProperty("scaleTargetRef")
    @Required
    @JsonSetter(nulls = Nulls.SKIP)
    private ScaleTargetRef scaleTargetRef;

    public ScaleTargetRef getScaleTargetRef() {
        return scaleTargetRef;
    }

    public void setScaleTargetRef(ScaleTargetRef scaleTargetRef) {
        this.scaleTargetRef = scaleTargetRef;
    }

    @JsonProperty("topicName")
    @JsonSetter(nulls = Nulls.SKIP)
    private String topicName;

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    @JsonProperty("triggers")
    @Required
    @JsonSetter(nulls = Nulls.SKIP)
    private List<Triggers> triggers = Serialization.unmarshal("[]", List.class);

    public List<Triggers> getTriggers() {
        return triggers;
    }

    public void setTriggers(List<Triggers> triggers) {
        this.triggers = triggers;
    }
}

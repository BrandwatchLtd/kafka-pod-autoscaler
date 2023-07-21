package com.brandwatch.kafka_pod_autoscaler.v1alpha1;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Singular;
import io.fabric8.kubernetes.model.annotation.Version;

@Version("v1alpha1")
@Group("com.brandwatch")
@Singular("kafkapodautoscaler")
@Plural("kafkapodautoscalers")
@ShortNames({"kpa", "kpas"})
public class KafkaPodAutoscaler extends CustomResource<KafkaPodAutoscalerSpec, KafkaPodAutoscalerStatus> implements Namespaced {
}

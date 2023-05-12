package com.brandwatch.kafka_pod_autoscaler;

import java.time.Duration;
import java.util.Map;

import brandwatch.com.v1alpha1.KafkaPodAutoscaler;
import brandwatch.com.v1alpha1.kafkapodautoscalerspec.ScaleTargetRef;
import brandwatch.com.v1alpha1.kafkapodautoscalerspec.Triggers;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ControllerConfiguration
public class KafkaPodAutoscalerReconciler implements Reconciler<KafkaPodAutoscaler> {
    private final boolean doScale;

    public KafkaPodAutoscalerReconciler(boolean doScale) {
        this.doScale = doScale;
    }

    @Override
    public UpdateControl<KafkaPodAutoscaler> reconcile(KafkaPodAutoscaler kafkaPodAutoscaler, Context<KafkaPodAutoscaler> context) {
        var targetName = kafkaPodAutoscaler.getSpec().getScaleTargetRef().getName();
        var targetKind = kafkaPodAutoscaler.getSpec().getScaleTargetRef().getKind();
        var resource = getGenericResource(context.getClient(), kafkaPodAutoscaler.getMetadata().getNamespace(),
                                          kafkaPodAutoscaler.getSpec().getScaleTargetRef());

        if (!resource.isReady()) {
            kafkaPodAutoscaler.getStatus().setMessage(targetKind + " is not ready. Skipping scale");
            return UpdateControl.patchStatus(kafkaPodAutoscaler)
                                .rescheduleAfter(Duration.ofSeconds(10));
        }

        var currentReplicaCount = getReplicaCount(resource);
        var idealReplicaCount = kafkaPodAutoscaler.getSpec().getTriggers().stream()
                                                  .mapToInt(trigger -> calculateTriggerResult(trigger).recommendedReplicas())
                                                  .max().orElse(1);
        var bestReplicaCount = fitReplicaCount(idealReplicaCount, getPartitionCount(kafkaPodAutoscaler));

        if (currentReplicaCount != bestReplicaCount) {
            if (doScale) {
                resource.scale(bestReplicaCount, false);
            } else {
                logger.info("Scaling deployment {} to {} replicas", targetName, bestReplicaCount);
            }
            // TODO: Expand reason, statuses for each trigger?
            kafkaPodAutoscaler.getStatus().setMessage(targetKind + " being scaled from " + currentReplicaCount
                                                              + " to " + bestReplicaCount + "replicas");
        } else {
            kafkaPodAutoscaler.getStatus().setMessage(targetKind + " is correctly scaled to " + bestReplicaCount + "replicas");
        }

        return UpdateControl.patchStatus(kafkaPodAutoscaler)
                            // TODO: Backoff if scaled up/down - allow this to be configurable
                            .rescheduleAfter(Duration.ofSeconds(10));
    }

    private Resource<GenericKubernetesResource> getGenericResource(KubernetesClient client, String namespace, ScaleTargetRef scaleTargetRef) {
        return client.genericKubernetesResources(new ResourceDefinitionContext.Builder()
                                                         .withKind(scaleTargetRef.getKind())
                                                         .build())
                     .inNamespace(namespace)
                     .withName(scaleTargetRef.getName());
    }

    private int getReplicaCount(Resource<GenericKubernetesResource> resource) {
        return (int) ((Map) resource.get().getAdditionalProperties().get("spec")).get("replicaCount");
    }

    private int getPartitionCount(KafkaPodAutoscaler kafkaPodAutoscaler) {
        throw new UnsupportedOperationException("TODO");
    }

    private TriggerResult calculateTriggerResult(Triggers trigger) {
        throw new UnsupportedOperationException("TODO");
    }

    private int fitReplicaCount(int idealReplicaCount, int partitionCount) {
        if (idealReplicaCount == 0) {
            return 1;
        }
        for (var i = idealReplicaCount; i <= partitionCount; i++) {
            if ((partitionCount / (double) i) != Math.floorDiv(partitionCount, i)) {
                continue;
            }
            return i;
        }
        return partitionCount;
    }

    record TriggerResult(Triggers trigger, int recommendedReplicas) {
    }
}

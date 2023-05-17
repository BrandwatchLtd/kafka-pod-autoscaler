package com.brandwatch.kafka_pod_autoscaler;

import java.time.Duration;
import java.util.OptionalInt;
import java.util.ServiceLoader;

import brandwatch.com.v1alpha1.KafkaPodAutoscaler;
import brandwatch.com.v1alpha1.KafkaPodAutoscalerStatus;
import brandwatch.com.v1alpha1.kafkapodautoscalerspec.ScaleTargetRef;
import brandwatch.com.v1alpha1.kafkapodautoscalerspec.Triggers;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.scaledresources.GenericScaledResourceFactory;

@Slf4j
@ControllerConfiguration
public class KafkaPodAutoscalerReconciler implements Reconciler<KafkaPodAutoscaler> {
    private final boolean doScale;
    private final PartitionCountFetcher partitionCountFetcher;

    public KafkaPodAutoscalerReconciler(boolean doScale, PartitionCountFetcher partitionCountFetcher) {
        this.doScale = doScale;
        this.partitionCountFetcher = partitionCountFetcher;
    }

    @Override
    public UpdateControl<KafkaPodAutoscaler> reconcile(KafkaPodAutoscaler kafkaPodAutoscaler, Context<KafkaPodAutoscaler> context) {
        var targetName = kafkaPodAutoscaler.getSpec().getScaleTargetRef().getName();
        var targetKind = kafkaPodAutoscaler.getSpec().getScaleTargetRef().getKind();
        var statusLogger = new StatusLogger(kafkaPodAutoscaler);
        var resource = getScaledResource(context.getClient(), kafkaPodAutoscaler.getMetadata().getNamespace(),
                                         kafkaPodAutoscaler.getSpec().getScaleTargetRef());

        if (resource == null) {
            statusLogger.log(targetKind + " not found. Skipping scale");
            return UpdateControl.patchStatus(kafkaPodAutoscaler)
                                .rescheduleAfter(Duration.ofSeconds(10));
        }

        if (!resource.isReady()) {
            statusLogger.log(targetKind + " is not ready. Skipping scale");
            return UpdateControl.patchStatus(kafkaPodAutoscaler)
                                .rescheduleAfter(Duration.ofSeconds(10));
        }

        if (resource.getReplicaCount() == 0) {
            statusLogger.log(targetKind + " has been scaled to zero. Skipping scale");
            return UpdateControl.patchStatus(kafkaPodAutoscaler)
                                .rescheduleAfter(Duration.ofSeconds(10));
        }

        var currentReplicaCount = resource.getReplicaCount();
        var idealReplicaCount = kafkaPodAutoscaler.getSpec().getTriggers().stream()
                                                  .mapToInt(trigger -> calculateTriggerResult(trigger).recommendedReplicas())
                                                  .max().orElse(1);
        var partitionCount = getPartitionCount(kafkaPodAutoscaler).orElse(idealReplicaCount);
        var bestReplicaCount = fitReplicaCount(idealReplicaCount, partitionCount);

        if (currentReplicaCount != bestReplicaCount) {
            if (doScale) {
                resource.scale(bestReplicaCount);
            } else {
                logger.info("Scaling deployment {} to {} replicas", targetName, bestReplicaCount);
            }
            // TODO: Expand reason, statuses for each trigger?
            statusLogger.log(targetKind + " being scaled from " + currentReplicaCount
                                     + " to " + bestReplicaCount + " replicas");
        } else {
            statusLogger.log(targetKind + " is correctly scaled to " + bestReplicaCount + "replicas");
        }

        return UpdateControl.patchStatus(kafkaPodAutoscaler)
                            // TODO: Backoff if scaled up/down - allow this to be configurable
                            .rescheduleAfter(Duration.ofSeconds(10));
    }

    private ScaledResource getScaledResource(KubernetesClient client, String namespace, ScaleTargetRef scaleTargetRef) {
        var factories = ServiceLoader.load(ScaledResourceFactory.class);

        logger.debug("Attempting to find resource to scale: {}", refToString(scaleTargetRef));
        return factories.stream()
                .map(ServiceLoader.Provider::get)
                .filter(factory -> factory.supports(client, namespace, scaleTargetRef))
                .peek(factory -> logger.info("Found factory that supports {}: {} (only the first will be used)",
                                             refToString(scaleTargetRef), factory))
                .findFirst()
                .orElseGet(GenericScaledResourceFactory::new)
                .create(client, namespace, scaleTargetRef);
    }

    private String refToString(ScaleTargetRef scaleTargetRef) {
        if (scaleTargetRef.getApiVersion() == null) {
            return scaleTargetRef.getKind() + "/" + scaleTargetRef.getName();
        }
        return scaleTargetRef.getApiVersion() + "." + scaleTargetRef.getKind() + "/" + scaleTargetRef.getName();
    }

    private OptionalInt getPartitionCount(KafkaPodAutoscaler kafkaPodAutoscaler) {
        var consumerGroup = kafkaPodAutoscaler.getSpec().getBootstrapServers();
        if (consumerGroup == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(partitionCountFetcher.countPartitions(
                kafkaPodAutoscaler.getSpec().getBootstrapServers(),
                kafkaPodAutoscaler.getSpec().getTopicName()
        ));
    }

    private TriggerResult calculateTriggerResult(Triggers trigger) {
        if (trigger.getType().equals("static")) {
            return new TriggerResult(trigger, Integer.parseInt(trigger.getMetadata().get("replicas")));
        }
        throw new UnsupportedOperationException("Unsupported trigger \"" + trigger.getType() + "\"");
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

    private static class StatusLogger {
        private final String name;
        private final KafkaPodAutoscalerStatus status;

        public StatusLogger(KafkaPodAutoscaler kafkaPodAutoscaler) {
            name = kafkaPodAutoscaler.getMetadata().getName();
            status = new KafkaPodAutoscalerStatus();
            kafkaPodAutoscaler.setStatus(status);
        }

        public void log(String message) {
            logger.info("Setting status on autoscaler {} to: {}", name, message);
            status.setMessage(message);
        }
    }
}

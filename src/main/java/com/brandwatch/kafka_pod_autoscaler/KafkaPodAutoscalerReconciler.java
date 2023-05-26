package com.brandwatch.kafka_pod_autoscaler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.OptionalInt;
import java.util.ServiceLoader;

import brandwatch.com.v1alpha1.KafkaPodAutoscaler;
import brandwatch.com.v1alpha1.KafkaPodAutoscalerStatus;
import brandwatch.com.v1alpha1.kafkapodautoscalerspec.ScaleTargetRef;
import brandwatch.com.v1alpha1.kafkapodautoscalerspec.Triggers;
import brandwatch.com.v1alpha1.kafkapodautoscalerstatus.TriggerResults;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.scaledresources.GenericScaledResourceFactory;
import com.brandwatch.kafka_pod_autoscaler.triggers.TriggerProcessor;
import com.brandwatch.kafka_pod_autoscaler.triggers.TriggerResult;

@Slf4j
@ControllerConfiguration
public class KafkaPodAutoscalerReconciler implements Reconciler<KafkaPodAutoscaler> {
    private final PartitionCountFetcher partitionCountFetcher;

    public KafkaPodAutoscalerReconciler(PartitionCountFetcher partitionCountFetcher) {
        this.partitionCountFetcher = partitionCountFetcher;
    }

    @Override
    public UpdateControl<KafkaPodAutoscaler> reconcile(KafkaPodAutoscaler kafkaPodAutoscaler, Context<KafkaPodAutoscaler> context) {
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
        var calculatedReplicaCount = kafkaPodAutoscaler.getSpec().getTriggers().stream()
            .map(trigger -> calculateTriggerResult(context.getClient(), resource, kafkaPodAutoscaler, trigger, currentReplicaCount))
            .mapToInt(r -> {
                var replicas = r.recommendedReplicas(currentReplicaCount);

                statusLogger.recordTriggerResult(r, replicas);

                return replicas;
            })
            .max().orElse(1);
        var partitionCount = getPartitionCount(kafkaPodAutoscaler);
        var finalReplicaCount = fitReplicaCount(calculatedReplicaCount, partitionCount.orElse(calculatedReplicaCount));

        if (partitionCount.isPresent()) {
            statusLogger.recordPartitionCount(partitionCount.getAsInt());
        }
        statusLogger.recordCurrentReplicaCount(currentReplicaCount);
        statusLogger.recordCalculatedReplicaCount(calculatedReplicaCount);
        statusLogger.recordFinalReplicaCount(finalReplicaCount);

        if (currentReplicaCount != finalReplicaCount) {
            if (!kafkaPodAutoscaler.getSpec().getDryRun()) {
                resource.scale(finalReplicaCount);
            }
            statusLogger.log(targetKind + " being scaled from " + currentReplicaCount
                                     + " to " + finalReplicaCount + " replicas");
        } else {
            statusLogger.log(targetKind + " is correctly scaled to " + finalReplicaCount + "replicas");
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

    private TriggerResult calculateTriggerResult(KubernetesClient client, ScaledResource scaledResource,
                                                 KafkaPodAutoscaler autoscaler, @NonNull Triggers trigger, int replicaCount) {
        var type = trigger.getType();
        var processors = ServiceLoader.load(TriggerProcessor.class);

        logger.debug("Attempting to find trigger processor for: {}", type);
        return processors.stream()
                         .map(ServiceLoader.Provider::get)
                         .filter(processor -> processor.getType().equals(type))
                         .peek(processor -> logger.info("Found trigger processor that supports {}: {} (only the first will be used)",
                                                        type, processor))
                         .findFirst()
                         .orElseThrow(() -> new UnsupportedOperationException("Count not find trigger processor for type: " + type))
                         .process(client, scaledResource, autoscaler, trigger, replicaCount);
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

    private static class StatusLogger {
        private final String name;
        private final KafkaPodAutoscalerStatus status;

        public StatusLogger(KafkaPodAutoscaler kafkaPodAutoscaler) {
            name = kafkaPodAutoscaler.getMetadata().getName();
            status = new KafkaPodAutoscalerStatus();
            status.setTriggerResults(new ArrayList<>());
            kafkaPodAutoscaler.setStatus(status);
        }

        public void log(String message) {
            logger.info("Setting status on autoscaler {} to: {}", name, message);
            status.setTimestamp(Instant.now().toString());
            status.setMessage(message);
        }

        public void recordTriggerResult(TriggerResult result, int recommendedReplicas) {
            var triggerResults = new TriggerResults();
            triggerResults.setType(result.trigger().getType());
            triggerResults.setInputValue(result.inputValue());
            triggerResults.setTargetThreshold(result.targetThreshold());
            triggerResults.setRecommendedReplicas(recommendedReplicas);

            status.getTriggerResults()
                  .add(triggerResults);
        }

        public void recordPartitionCount(int partitionCount) {
            status.setPartitionCount(partitionCount);
        }

        public void recordCurrentReplicaCount(int currentReplicaCount) {
            status.setCurrentReplicaCount(currentReplicaCount);
        }

        public void recordCalculatedReplicaCount(int calculatedReplicaCount) {
            status.setCalculatedReplicaCount(calculatedReplicaCount);
        }

        public void recordFinalReplicaCount(int finalReplicaCount) {
            status.setFinalReplicaCount(finalReplicaCount);
        }
    }
}

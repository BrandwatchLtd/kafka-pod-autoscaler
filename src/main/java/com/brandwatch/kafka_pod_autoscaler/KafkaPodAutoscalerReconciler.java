package com.brandwatch.kafka_pod_autoscaler;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.ServiceLoader;

import brandwatch.com.v1alpha1.KafkaPodAutoscaler;
import brandwatch.com.v1alpha1.KafkaPodAutoscalerStatus;
import brandwatch.com.v1alpha1.kafkapodautoscalerspec.ScaleTargetRef;
import brandwatch.com.v1alpha1.kafkapodautoscalerspec.Triggers;
import brandwatch.com.v1alpha1.kafkapodautoscalerstatus.TriggerResults;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.api.model.MicroTime;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import com.brandwatch.kafka_pod_autoscaler.metrics.ScalerMetrics;
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
        var statusLogger = new StatusLogger(context.getClient(), kafkaPodAutoscaler);
        var resource = getScaledResource(context.getClient(), kafkaPodAutoscaler.getMetadata().getNamespace(),
                                         kafkaPodAutoscaler.getSpec().getScaleTargetRef());

        if (resource == null) {
            statusLogger.clearStatus();
            statusLogger.setScaleable(false);
            statusLogger.log(targetKind + " not found. Skipping scale");
            return UpdateControl.patchStatus(kafkaPodAutoscaler)
                                .rescheduleAfter(Duration.ofSeconds(10));
        }

        if (resource.getReplicaCount() == 0) {
            statusLogger.clearStatus();
            statusLogger.setScaleable(false);
            statusLogger.log(targetKind + " has been scaled to zero. Skipping scale");
            return UpdateControl.patchStatus(kafkaPodAutoscaler)
                                .rescheduleAfter(Duration.ofSeconds(10));
        }

        if (!resource.isReady()) {
            statusLogger.setScaleable(false);
            statusLogger.log(targetKind + " is not ready. Skipping scale");
            return UpdateControl.patchStatus(kafkaPodAutoscaler)
                .rescheduleAfter(Duration.ofSeconds(10));
        }

        var currentReplicaCount = kafkaPodAutoscaler.getSpec().getDryRun()
                ? Optional.ofNullable(kafkaPodAutoscaler.getStatus().getDryRunReplicas())
                          .orElse(resource.getReplicaCount())
                : resource.getReplicaCount();

        statusLogger.clearTriggerResults();
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
            var rescaleWindow = Instant.now().minus(Duration.ofSeconds(kafkaPodAutoscaler.getSpec().getCooloffSeconds()));
            if (statusLogger.getLastScale() != null && statusLogger.getLastScale().isAfter(rescaleWindow)) {
                statusLogger.setScaleable(false);
                statusLogger.log(targetKind + " has been scaled recently. Skipping scale");
                return UpdateControl.patchStatus(kafkaPodAutoscaler)
                                    .rescheduleAfter(Duration.ofSeconds(10));
            }

            if (!kafkaPodAutoscaler.getSpec().getDryRun()) {
                statusLogger.setDryRunReplicas(null);
                resource.scale(finalReplicaCount);
                statusLogger.log(targetKind + " being scaled from " + currentReplicaCount
                                         + " to " + finalReplicaCount + " replicas");
            } else {
                statusLogger.setDryRunReplicas(finalReplicaCount);
                statusLogger.log(targetKind + " dry-run scaled from " + currentReplicaCount
                                         + " to " + finalReplicaCount + " replicas");
            }
            statusLogger.recordLastScale();
        } else {
            statusLogger.log(targetKind + " is correctly scaled to " + finalReplicaCount + " replicas");
        }
        statusLogger.setScaleable(true);

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
                .peek(factory -> logger.debug("Found factory that supports {}: {} (only the first will be used)",
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
                         .peek(processor -> logger.debug("Found trigger processor that supports {}: {} (only the first will be used)",
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
        return Math.max(partitionCount, 1);
    }

    static class StatusLogger {
        private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

        private final KubernetesClient client;
        private final KafkaPodAutoscaler kafkaPodAutoscaler;
        private final String name;
        @Getter
        private final Instant lastScale;
        private final KafkaPodAutoscalerStatus status;
        private final ScalerMetrics scalerMetrics;

        public StatusLogger(KubernetesClient client, KafkaPodAutoscaler kafkaPodAutoscaler) {
            this.client = client;
            this.kafkaPodAutoscaler = kafkaPodAutoscaler;
            name = kafkaPodAutoscaler.getMetadata().getName();
            lastScale = Optional.ofNullable(kafkaPodAutoscaler.getStatus())
                    .map(KafkaPodAutoscalerStatus::getLastScale)
                    .map(DATE_TIME_FORMATTER::parse)
                    .map(Instant::from)
                    .orElse(null);
            status = Optional.ofNullable(kafkaPodAutoscaler.getStatus())
                    .orElseGet(KafkaPodAutoscalerStatus::new);
            this.scalerMetrics = ScalerMetrics.getOrCreate(kafkaPodAutoscaler.getMetadata().getNamespace(), name);
            if (status.getTriggerResults() == null) {
                status.setTriggerResults(new ArrayList<>());
            }
            kafkaPodAutoscaler.setStatus(status);
        }

        public void log(String message) {
            var lastMessage = status.getMessage();

            status.setTimestamp(Instant.now().toString());
            if (!Objects.equals(lastMessage, message)) {
                logger.info("Setting status on autoscaler {} to: {}", name, message);
                status.setMessage(message);

                final DateTimeFormatter k8sMicroTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'.'SSSSSSXXX");

                var event = new EventBuilder()
                        .withMetadata(new ObjectMetaBuilder()
                                              .withGenerateName(name)
                                              .withNamespace(kafkaPodAutoscaler.getMetadata().getNamespace())
                                              .build())
                        .withReportingComponent("kafka-pod-autoscaler")
                        .withReportingInstance(name)
                        .withAction("Info")
                        .withType("Normal")
                        .withMessage(message)
                        .withReason("ScaleDecision")
                        .withInvolvedObject(new ObjectReferenceBuilder()
                                                    .withApiVersion(kafkaPodAutoscaler.getApiVersion())
                                                    .withKind(kafkaPodAutoscaler.getKind())
                                                    .withName(name)
                                                    .withNamespace(kafkaPodAutoscaler.getMetadata().getNamespace())
                                                    .withResourceVersion(kafkaPodAutoscaler.getMetadata().getResourceVersion())
                                                    .build())
                        .withEventTime(new MicroTime(k8sMicroTime.format(Instant.now().atZone(ZoneOffset.UTC))))
                        .build();

                client.v1().events().resource(event).create();
            }
        }

        public void recordTriggerResult(TriggerResult result, int recommendedReplicas) {
            var triggerResults = new TriggerResults();
            triggerResults.setType(result.trigger().getType());
            triggerResults.setInputValue(result.inputValue());
            triggerResults.setTargetThreshold(result.targetThreshold());
            triggerResults.setRecommendedReplicas(recommendedReplicas);

            status.getTriggerResults()
                  .add(triggerResults);

            scalerMetrics.setTriggerMetrics(result, recommendedReplicas);
        }

        public void recordPartitionCount(int partitionCount) {
            scalerMetrics.setPartitionCount(partitionCount);
            status.setPartitionCount(partitionCount);
        }

        public void recordCurrentReplicaCount(int currentReplicaCount) {
            scalerMetrics.setCurrentReplicaCount(currentReplicaCount);
            status.setCurrentReplicaCount(currentReplicaCount);
        }

        public void recordCalculatedReplicaCount(int calculatedReplicaCount) {
            scalerMetrics.setCalculatedReplicaCount(calculatedReplicaCount);
            status.setCalculatedReplicaCount(calculatedReplicaCount);
        }

        public void recordFinalReplicaCount(int finalReplicaCount) {
            scalerMetrics.setFinalReplicaCount(finalReplicaCount);
            status.setFinalReplicaCount(finalReplicaCount);
        }

        public void setDryRunReplicas(Integer dryRunReplicas) {
            scalerMetrics.setDryRunReplicas(dryRunReplicas);
            status.setDryRunReplicas(dryRunReplicas);
        }

        public void recordLastScale() {
            scalerMetrics.setLastScale(Instant.now().toEpochMilli());
            status.setLastScale(DATE_TIME_FORMATTER.format(Instant.now().atZone(ZoneOffset.UTC)));
        }

        public void setScaleable(boolean scalable) {
            scalerMetrics.setScalable(scalable);
        }

        public void clearStatus() {
            status.setLastScale(null);
            status.setDryRunReplicas(null);
            status.setPartitionCount(null);
            status.setFinalReplicaCount(null);
            status.setCurrentReplicaCount(null);
            status.setCalculatedReplicaCount(null);
            status.setCalculatedReplicaCount(null);
            status.setMessage(null);
            status.setTimestamp(null);
        }

        public void clearTriggerResults() {
            status.setTriggerResults(new ArrayList<>());
        }
    }
}

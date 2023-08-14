package com.brandwatch.kafka_pod_autoscaler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import com.brandwatch.kafka_pod_autoscaler.metrics.ScalerMetrics;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.KafkaPodAutoscaler;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.KafkaPodAutoscalerSpec;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.KafkaPodAutoscalerStatus;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerspec.ScaleTargetRef;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerspec.TriggerDefinition;
import com.brandwatch.kafka_pod_autoscaler.v1alpha1.kafkapodautoscalerstatus.TriggerResult;

@ExtendWith(MockitoExtension.class)
public class KafkaPodAutoscalerReconcilerTest {
    private static final Instant NOW = Instant.parse("2000-01-02T03:04:05.006Z");
    private static final String NAMESPACE = "test-ns";
    private static final String DEPLOYMENT_NAME = "test-deploy";

    @Mock
    private Context<KafkaPodAutoscaler> mockContext;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private KubernetesClient client;
    @Mock
    private RollableScalableResource<Deployment> deploymentResource;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Deployment deployment;
    @Mock
    private PartitionCountFetcher partitionCountFetcher;
    private KafkaPodAutoscalerReconciler reconciler;
    private KafkaPodAutoscaler kpa;

    @BeforeEach
    public void beforeEach() {
        reconciler = new KafkaPodAutoscalerReconciler(partitionCountFetcher, Clock.fixed(NOW, ZoneOffset.UTC));

        lenient().when(mockContext.getClient()).thenReturn(client);
        @SuppressWarnings("unchecked")
        var namespaceOp = (NonNamespaceOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>>)
                mock(NonNamespaceOperation.class, Answers.RETURNS_SELF);
        lenient().when(client.apps().deployments().inNamespace(NAMESPACE))
                .thenReturn(namespaceOp);
        lenient().when(namespaceOp.withName(DEPLOYMENT_NAME)).thenReturn(deploymentResource);
        lenient().when(deploymentResource.get()).thenReturn(deployment);
        lenient().when(deploymentResource.isReady()).thenReturn(true);

        lenient().when(deployment.getSpec().getReplicas()).thenReturn(1);

        @SuppressWarnings("unchecked")
        var resourceEvent = (Resource<Event>) mock(Resource.class);
        lenient().when(client.v1().events().resource(any())).thenReturn(resourceEvent);

        var metadata = new ObjectMeta();
        metadata.setName("kpa");
        metadata.setNamespace(NAMESPACE);
        var scaleTargetRef = new ScaleTargetRef();
        scaleTargetRef.setKind("Deployment");
        scaleTargetRef.setName(DEPLOYMENT_NAME);
        var spec = new KafkaPodAutoscalerSpec();
        spec.setScaleTargetRef(scaleTargetRef);
        spec.setMaxScaleIncrements(100);
        kpa = new KafkaPodAutoscaler();
        kpa.setMetadata(metadata);
        kpa.setSpec(spec);
    }

    @Test
    public void reconcile_missingResource() {
        when(deploymentResource.get()).thenReturn(null);

        var updateControl = reconciler.reconcile(kpa, mockContext);

        verify(deploymentResource, never()).scale(anyInt());
        assertThat(updateControl.getResource().getStatus().getMessage())
            .isEqualTo("Deployment not found. Skipping scale");

        var metrics = getMetrics();
        assertThat(metrics.isScalable()).isEqualTo(false);
    }

    @Test
    public void notReadyResource() {
        var status = new KafkaPodAutoscalerStatus();
        status.setLastScale("2000-01-02T03:04:05.006Z");
        kpa.setStatus(status);

        when(deploymentResource.isReady()).thenReturn(false);

        var updateControl = reconciler.reconcile(kpa, mockContext);

        verify(deploymentResource, never()).scale(anyInt());
        assertThat(updateControl.getResource().getStatus().getMessage())
            .isEqualTo("Deployment is not ready. Skipping scale");
        assertThat(updateControl.getResource().getStatus().getLastScale())
            .isEqualTo("2000-01-02T03:04:05.006Z");

        var metrics = getMetrics();
        assertThat(metrics.isScalable()).isEqualTo(false);
    }

    @Test
    public void scaledToZeroResource() {
        when(deployment.getSpec().getReplicas()).thenReturn(0);

        var updateControl = reconciler.reconcile(kpa, mockContext);

        verify(deploymentResource, never()).scale(anyInt());
        assertThat(updateControl.getResource().getStatus().getMessage())
                .isEqualTo("Deployment has been scaled to zero. Skipping scale");
        assertThat(updateControl.getResource().getStatus().getCurrentReplicaCount())
                .isEqualTo(null);
        assertThat(updateControl.getResource().getStatus().getPartitionCount())
                .isEqualTo(null);
        assertThat(updateControl.getResource().getStatus().getCalculatedReplicaCount())
                .isEqualTo(null);
        assertThat(updateControl.getResource().getStatus().getFinalReplicaCount())
                .isEqualTo(null);
        assertThat(updateControl.getResource().getStatus().getTriggerResults())
                .isEqualTo(List.of());

        var metrics = getMetrics();
        assertThat(metrics.isScalable()).isEqualTo(false);
    }

    @Test
    public void scaledWithinCooloff() {
        var staticTrigger = new TriggerDefinition();
        staticTrigger.setType("static");
        staticTrigger.setMetadata(Map.of("replicas", "3"));
        kpa.getSpec().setTriggers(List.of(
            staticTrigger
        ));
        when(deployment.getSpec().getReplicas()).thenReturn(1);

        var updateControl = reconciler.reconcile(kpa, mockContext);
        var lastScale = updateControl.getResource().getStatus().getLastScale();
        verify(deploymentResource).scale(3);

        for (var i = 0; i < 5; i++) {
            // skip clock forward
            reconciler.setClock(Clock.fixed(NOW.plusSeconds(30), ZoneOffset.UTC));

            updateControl = reconciler.reconcile(kpa, mockContext);

            verify(deploymentResource).scale(3);
            assertThat(updateControl.getResource().getStatus().getMessage())
                .isEqualTo("Deployment has been scaled recently. Skipping scale");
            assertThat(updateControl.getResource().getStatus().getLastScale())
                .isEqualTo(lastScale);
            assertThat(updateControl.getResource().getStatus().getCurrentReplicaCount())
                .isEqualTo(1);
            assertThat(updateControl.getResource().getStatus().getPartitionCount())
                .isEqualTo(null);
            assertThat(updateControl.getResource().getStatus().getCalculatedReplicaCount())
                .isEqualTo(3);
            assertThat(updateControl.getResource().getStatus().getFinalReplicaCount())
                .isEqualTo(3);

            var metrics = getMetrics();
            assertThat(metrics.isScalable()).isEqualTo(false);
        }
    }

    @Test
    public void returnsToOriginalScalingWithinCooloff() {
        var staticTrigger = new TriggerDefinition();
        staticTrigger.setType("static");
        staticTrigger.setMetadata(Map.of("replicas", "3"));
        kpa.getSpec().setTriggers(List.of(
            staticTrigger
        ));
        when(deployment.getSpec().getReplicas()).thenReturn(1);

        var updateControl = reconciler.reconcile(kpa, mockContext);
        var lastScale = updateControl.getResource().getStatus().getLastScale();
        verify(deploymentResource).scale(3);

        when(deployment.getSpec().getReplicas()).thenReturn(3);
        // skip clock forward
        reconciler.setClock(Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC));

        updateControl = reconciler.reconcile(kpa, mockContext);

        verify(deploymentResource).scale(anyInt());
        assertThat(updateControl.getResource().getStatus().getMessage())
            .isEqualTo("Deployment is correctly scaled to 3 replicas");
        assertThat(updateControl.getResource().getStatus().getLastScale())
            .isEqualTo(lastScale);
        assertThat(updateControl.getResource().getStatus().getCurrentReplicaCount())
            .isEqualTo(3);
        assertThat(updateControl.getResource().getStatus().getPartitionCount())
            .isEqualTo(null);
        assertThat(updateControl.getResource().getStatus().getCalculatedReplicaCount())
            .isEqualTo(3);
        assertThat(updateControl.getResource().getStatus().getFinalReplicaCount())
            .isEqualTo(3);

        var metrics = getMetrics();
        assertThat(metrics.isScalable()).isEqualTo(false);
    }

    @Test
    public void coolOffPeriodExpires() {
        var staticTrigger = new TriggerDefinition();
        staticTrigger.setType("static");
        staticTrigger.setMetadata(Map.of("replicas", "3"));
        kpa.getSpec().setTriggers(List.of(
            staticTrigger
        ));
        when(deployment.getSpec().getReplicas()).thenReturn(1);

        var updateControl = reconciler.reconcile(kpa, mockContext);
        var lastScale = updateControl.getResource().getStatus().getLastScale();
        verify(deploymentResource).scale(3);

        when(deployment.getSpec().getReplicas()).thenReturn(3);

        // skip clock forward
        var newNow = NOW.plusSeconds(301);
        reconciler.setClock(Clock.fixed(newNow, ZoneOffset.UTC));

        staticTrigger.setMetadata(Map.of("replicas", "1"));
        updateControl = reconciler.reconcile(kpa, mockContext);

        verify(deploymentResource).scale(1);

        assertThat(updateControl.getResource().getStatus().getMessage())
            .isEqualTo("Deployment being scaled from 3 to 1 replicas");
        assertThat(updateControl.getResource().getStatus().getLastScale())
            .isEqualTo(KafkaPodAutoscalerReconciler.DATE_TIME_FORMATTER.format(newNow.atZone(ZoneOffset.UTC)));
        assertThat(updateControl.getResource().getStatus().getCurrentReplicaCount())
            .isEqualTo(3);
        assertThat(updateControl.getResource().getStatus().getPartitionCount())
            .isEqualTo(null);
        assertThat(updateControl.getResource().getStatus().getCalculatedReplicaCount())
            .isEqualTo(1);
        assertThat(updateControl.getResource().getStatus().getFinalReplicaCount())
            .isEqualTo(1);

        var metrics = getMetrics();
        assertThat(metrics.isScalable()).isEqualTo(true);
    }

    @Test
    public void canScale_withNoKafkaConfigOrTriggers() {
        when(deployment.getSpec().getReplicas()).thenReturn(3);

        var updateControl = reconciler.reconcile(kpa, mockContext);

        verify(deploymentResource).scale(1);
        assertThat(updateControl.getResource().getStatus().getMessage())
                .isEqualTo("Deployment being scaled from 3 to 1 replicas");
        assertThat(updateControl.getResource().getStatus().getCurrentReplicaCount())
                .isEqualTo(3);
        assertThat(updateControl.getResource().getStatus().getPartitionCount())
                .isEqualTo(null);
        assertThat(updateControl.getResource().getStatus().getCalculatedReplicaCount())
                .isEqualTo(1);
        assertThat(updateControl.getResource().getStatus().getFinalReplicaCount())
                .isEqualTo(1);
        assertThat(updateControl.getResource().getStatus().getTriggerResults())
                .isEqualTo(List.of());

        var metrics = getMetrics();
        assertThat(metrics.isScalable()).isEqualTo(true);
    }

    @Test
    public void canScale_withNoKafkaConfig_staticTriggers() {
        var staticTrigger = new TriggerDefinition();
        staticTrigger.setType("static");
        staticTrigger.setMetadata(Map.of("replicas", "3"));
        kpa.getSpec().setTriggers(List.of(
                staticTrigger
        ));

        var updateControl = reconciler.reconcile(kpa, mockContext);

        verify(deploymentResource).scale(3);

        assertThat(updateControl.getResource().getStatus().getMessage())
                .isEqualTo("Deployment being scaled from 1 to 3 replicas");
        assertThat(updateControl.getResource().getStatus().getCurrentReplicaCount())
                .isEqualTo(1);
        assertThat(updateControl.getResource().getStatus().getPartitionCount())
                .isEqualTo(null);
        assertThat(updateControl.getResource().getStatus().getCalculatedReplicaCount())
                .isEqualTo(3);
        assertThat(updateControl.getResource().getStatus().getFinalReplicaCount())
                .isEqualTo(3);
        assertTriggerResults(updateControl, List.of(
                createTriggerResultDTO("static", 3, 1, 3)
        ));

        var metrics = getMetrics();
        assertThat(metrics.isScalable()).isEqualTo(true);
    }

    @Test
    public void canScale_withKafkaConfig_staticTriggers() {
        var staticTrigger = new TriggerDefinition();
        staticTrigger.setType("static");
        staticTrigger.setMetadata(Map.of("replicas", "3"));
        kpa.getSpec().setTriggers(List.of(
                staticTrigger
        ));
        kpa.getSpec().setBootstrapServers("servers");
        kpa.getSpec().setTopicName("topic");

        when(partitionCountFetcher.countPartitions("servers", "topic"))
                .thenReturn(4);

        var updateControl = reconciler.reconcile(kpa, mockContext);

        verify(deploymentResource).scale(4);

        assertThat(updateControl.getResource().getStatus().getMessage())
                .isEqualTo("Deployment being scaled from 1 to 4 replicas");
        assertThat(updateControl.getResource().getStatus().getCurrentReplicaCount())
                .isEqualTo(1);
        assertThat(updateControl.getResource().getStatus().getPartitionCount())
                .isEqualTo(4);
        assertThat(updateControl.getResource().getStatus().getCalculatedReplicaCount())
                .isEqualTo(3);
        assertThat(updateControl.getResource().getStatus().getFinalReplicaCount())
                .isEqualTo(4);
        assertTriggerResults(updateControl, List.of(
                createTriggerResultDTO("static", 3, 1, 3)
        ));

        var metrics = getMetrics();
        assertThat(metrics.isScalable()).isEqualTo(true);
    }

    @Test
    public void canScale_withKafkaConfig_multipleStaticTriggers() {
        var staticTrigger1 = new TriggerDefinition();
        staticTrigger1.setType("static");
        staticTrigger1.setMetadata(Map.of("replicas", "2"));
        var staticTrigger2 = new TriggerDefinition();
        staticTrigger2.setType("static");
        staticTrigger2.setMetadata(Map.of("replicas", "3"));
        kpa.getSpec().setTriggers(List.of(
                staticTrigger1,
                staticTrigger2
        ));
        kpa.getSpec().setBootstrapServers("servers");
        kpa.getSpec().setTopicName("topic");

        when(partitionCountFetcher.countPartitions("servers", "topic"))
                .thenReturn(4);

        var updateControl = reconciler.reconcile(kpa, mockContext);

        verify(deploymentResource).scale(4);

        assertThat(updateControl.getResource().getStatus().getMessage())
                .isEqualTo("Deployment being scaled from 1 to 4 replicas");
        assertThat(updateControl.getResource().getStatus().getCurrentReplicaCount())
                .isEqualTo(1);
        assertThat(updateControl.getResource().getStatus().getPartitionCount())
                .isEqualTo(4);
        assertThat(updateControl.getResource().getStatus().getCalculatedReplicaCount())
                .isEqualTo(3);
        assertThat(updateControl.getResource().getStatus().getFinalReplicaCount())
                .isEqualTo(4);
        assertTriggerResults(updateControl, List.of(
                createTriggerResultDTO("static", 2, 1, 2),
                createTriggerResultDTO("static", 3, 1, 3)
        ));

        var metrics = getMetrics();
        assertThat(metrics.isScalable()).isEqualTo(true);
    }

    @ParameterizedTest
    @MethodSource
    public void fitReplicaCount(int currentReplicaCount, int idealReplicaCount, int partitionCount, int maxScaleIncrements,
                                int expectedResult) {
        assertThat(KafkaPodAutoscalerReconciler.fitReplicaCount(currentReplicaCount, idealReplicaCount, partitionCount, maxScaleIncrements))
            .isEqualTo(expectedResult);
    }

    public static Stream<Arguments> fitReplicaCount() {
        return Stream.of(
            // Scaling up
            Arguments.of(1, 1, 16, 1000, 1),
            Arguments.of(1, 3, 16, 1000, 4),
            Arguments.of(4, 200, 16, 1000, 16),
            Arguments.of(1, 3, 16, 1, 2),
            // Scaling down
            Arguments.of(16, 3, 16, 1000, 4),
            Arguments.of(16, 3, 16, 1, 8)
        );
    }

    private void assertTriggerResults(UpdateControl<KafkaPodAutoscaler> updateControl, List<TriggerResult> expectedResults) {
        var triggerResults = updateControl.getResource().getStatus().getTriggerResults();

        assertThat(triggerResults).hasSize(expectedResults.size());

        for (int i = 0; i < triggerResults.size(); i++) {
            var result = triggerResults.get(i);
            var expected = expectedResults.get(i);

            assertThat(result.getType()).isEqualTo(expected.getType());
            assertThat(result.getInputValue()).isEqualTo(expected.getInputValue());
            assertThat(result.getTargetThreshold()).isEqualTo(expected.getTargetThreshold());
            assertThat(result.getRecommendedReplicas()).isEqualTo(expected.getRecommendedReplicas());
        }
    }

    private static TriggerResult createTriggerResultDTO(String type, double inputValue, double targetThreshold, int recommendedReplicas) {
        var triggerResults = new TriggerResult();

        triggerResults.setType(type);
        triggerResults.setInputValue(inputValue);
        triggerResults.setTargetThreshold(targetThreshold);
        triggerResults.setRecommendedReplicas(recommendedReplicas);

        return triggerResults;
    }

    private ScalerMetrics getMetrics() {
        return ScalerMetrics.getOrCreate(kpa);
    }
}

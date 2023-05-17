package com.brandwatch.kafka_pod_autoscaler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import brandwatch.com.v1alpha1.KafkaPodAutoscaler;
import brandwatch.com.v1alpha1.KafkaPodAutoscalerSpec;
import brandwatch.com.v1alpha1.kafkapodautoscalerspec.ScaleTargetRef;
import brandwatch.com.v1alpha1.kafkapodautoscalerspec.Triggers;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;

@ExtendWith(MockitoExtension.class)
public class KafkaPodAutoscalerReconcilerTest {
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
    private KafkaPodAutoscalerReconciler reconciler;
    private KafkaPodAutoscaler kpa;

    @BeforeEach
    public void beforeEach() {
        reconciler = new KafkaPodAutoscalerReconciler(true);

        when(mockContext.getClient()).thenReturn(client);
        @SuppressWarnings("unchecked")
        var namespaceOp = (NonNamespaceOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>>)
                mock(NonNamespaceOperation.class, Answers.RETURNS_SELF);
        when(client.apps().deployments().inNamespace(NAMESPACE))
                .thenReturn(namespaceOp);
        when(namespaceOp.withName(DEPLOYMENT_NAME)).thenReturn(deploymentResource);
        lenient().when(deploymentResource.get()).thenReturn(deployment);
        lenient().when(deploymentResource.isReady()).thenReturn(true);

        lenient().when(deployment.getSpec().getReplicas()).thenReturn(1);

        var metadata = new ObjectMeta();
        metadata.setNamespace(NAMESPACE);
        var scaleTargetRef = new ScaleTargetRef();
        scaleTargetRef.setKind("Deployment");
        scaleTargetRef.setName(DEPLOYMENT_NAME);
        var spec = new KafkaPodAutoscalerSpec();
        spec.setScaleTargetRef(scaleTargetRef);
        kpa = new KafkaPodAutoscaler();
        kpa.setMetadata(metadata);
        kpa.setSpec(spec);
    }

    @Test
    public void reconcile_missingResource() {
        when(deploymentResource.get()).thenReturn(null);

        var updateControl = reconciler.reconcile(kpa, mockContext);

        assertThat(updateControl.getResource().getStatus().getMessage())
                .isEqualTo("Deployment not found. Skipping scale");
    }

    @Test
    public void notReadyResource() {
        when(deploymentResource.isReady()).thenReturn(false);

        var updateControl = reconciler.reconcile(kpa, mockContext);

        assertThat(updateControl.getResource().getStatus().getMessage())
                .isEqualTo("Deployment is not ready. Skipping scale");
    }

    @Test
    public void scaledToZeroResource() {
        when(deployment.getSpec().getReplicas()).thenReturn(0);

        var updateControl = reconciler.reconcile(kpa, mockContext);

        assertThat(updateControl.getResource().getStatus().getMessage())
                .isEqualTo("Deployment has been scaled to zero. Skipping scale");
    }

    @Test
    public void canScale_withNoKafkaConfigOrTriggers() {
        when(deployment.getSpec().getReplicas()).thenReturn(3);

        var updateControl = reconciler.reconcile(kpa, mockContext);

        assertThat(updateControl.getResource().getStatus().getMessage())
                .isEqualTo("Deployment being scaled from 3 to 1 replicas");
    }

    @Test
    public void canScale_withNoKafkaConfig_staticTriggers() {
        var staticTrigger = new Triggers();
        staticTrigger.setType("static");
        staticTrigger.setMetadata(Map.of("replicas", "3"));
        kpa.getSpec().setTriggers(List.of(
                staticTrigger
        ));

        var updateControl = reconciler.reconcile(kpa, mockContext);

        assertThat(updateControl.getResource().getStatus().getMessage())
                .isEqualTo("Deployment being scaled from 1 to 3 replicas");
    }
}

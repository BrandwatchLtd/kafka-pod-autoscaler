package com.brandwatch.kafka_pod_autoscaler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import brandwatch.com.v1alpha1.KafkaPodAutoscaler;
import brandwatch.com.v1alpha1.KafkaPodAutoscalerSpec;
import brandwatch.com.v1alpha1.kafkapodautoscalerspec.ScaleTargetRef;
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
    private KafkaPodAutoscalerReconciler reconciler;

    @BeforeEach
    public void beforeEach() {
        reconciler = new KafkaPodAutoscalerReconciler(true);

        when(mockContext.getClient()).thenReturn(client);
        var namespaceOp = (NonNamespaceOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>>)
                mock(NonNamespaceOperation.class, Answers.RETURNS_SELF);
        when(client.apps().deployments().inNamespace(NAMESPACE))
                .thenReturn(namespaceOp);
        var deployment = (RollableScalableResource<Deployment>) mock(RollableScalableResource.class);
        when(namespaceOp.withName(DEPLOYMENT_NAME)).thenReturn(deployment);
    }

    @Test
    public void reconcile_missingResource() {
        var metadata = new ObjectMeta();
        metadata.setNamespace(NAMESPACE);
        var scaleTargetRef = new ScaleTargetRef();
        scaleTargetRef.setKind("Deployment");
        scaleTargetRef.setName(DEPLOYMENT_NAME);
        var spec = new KafkaPodAutoscalerSpec();
        spec.setScaleTargetRef(scaleTargetRef);
        var kpa = new KafkaPodAutoscaler();
        kpa.setMetadata(metadata);
        kpa.setSpec(spec);

        var context = mockContext;

        var updateControl = reconciler.reconcile(kpa, context);

        assertThat(updateControl.getResource().getStatus().getMessage())
                .isEqualTo("Deployment not found. Skipping scale");
    }
}

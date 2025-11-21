package io.javaoperatorsdk.operator.workflow.workflowallfeature;

import java.time.Duration;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.workflow.workflowallfeature.ConfigMapDependentResource.READY_TO_DELETE_ANNOTATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Comprehensive workflow with reconcile and delete conditions",
    description =
        """
        Demonstrates a complete workflow implementation including reconcile conditions, delete \
        conditions, and ready conditions. Shows how to control when dependent resources are \
        created or deleted based on conditions, and how to coordinate dependencies that \
        must wait for others to be ready.
        """)
public class WorkflowAllFeatureIT {

  public static final String RESOURCE_NAME = "test";
  private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(WorkflowAllFeatureReconciler.class)
          .build();

  @Test
  void configMapNotReconciledUntilDeploymentReady() {
    operator.create(customResource(true));
    await()
        .untilAsserted(
            () -> {
              assertThat(
                      operator
                          .getReconcilerOfType(WorkflowAllFeatureReconciler.class)
                          .getNumberOfReconciliationExecution())
                  .isPositive();
              assertThat(operator.get(Deployment.class, RESOURCE_NAME)).isNotNull();
              assertThat(operator.get(ConfigMap.class, RESOURCE_NAME)).isNull();
              assertThat(getPrimaryStatus().getMsgFromCondition())
                  .isEqualTo(ConfigMapReconcileCondition.NOT_RECONCILED_YET);
            });

    await()
        .atMost(ONE_MINUTE)
        .untilAsserted(
            () -> {
              assertThat(
                      operator
                          .getReconcilerOfType(WorkflowAllFeatureReconciler.class)
                          .getNumberOfReconciliationExecution())
                  .isGreaterThan(1);
              assertThat(operator.get(ConfigMap.class, RESOURCE_NAME)).isNotNull();
              final var primaryStatus = getPrimaryStatus();
              assertThat(primaryStatus.getReady()).isTrue();
              assertThat(primaryStatus.getMsgFromCondition())
                  .isEqualTo(ConfigMapReconcileCondition.CREATE_SET);
            });

    markConfigMapForDelete();
  }

  private WorkflowAllFeatureStatus getPrimaryStatus() {
    return operator.get(WorkflowAllFeatureCustomResource.class, RESOURCE_NAME).getStatus();
  }

  @Test
  void configMapNotReconciledIfReconcileConditionNotMet() {
    var resource = operator.create(customResource(false));

    await()
        .atMost(ONE_MINUTE)
        .untilAsserted(
            () -> {
              assertThat(operator.get(ConfigMap.class, RESOURCE_NAME)).isNull();
              assertThat(getPrimaryStatus().getReady()).isTrue();
            });

    resource.getSpec().setCreateConfigMap(true);
    operator.replace(resource);

    await()
        .untilAsserted(
            () -> {
              assertThat(operator.get(ConfigMap.class, RESOURCE_NAME)).isNotNull();
              assertThat(getPrimaryStatus().getReady()).isTrue();
            });
  }

  @Test
  void configMapNotDeletedUntilNotMarked() {
    var resource = operator.create(customResource(true));

    await()
        .atMost(ONE_MINUTE)
        .untilAsserted(
            () -> {
              assertThat(getPrimaryStatus()).isNotNull();
              assertThat(getPrimaryStatus().getReady()).isTrue();
              assertThat(operator.get(ConfigMap.class, RESOURCE_NAME)).isNotNull();
            });

    operator.delete(resource);

    await()
        .pollDelay(Duration.ofMillis(300))
        .untilAsserted(
            () -> {
              assertThat(operator.get(ConfigMap.class, RESOURCE_NAME)).isNotNull();
              assertThat(operator.get(WorkflowAllFeatureCustomResource.class, RESOURCE_NAME))
                  .isNotNull();
            });

    markConfigMapForDelete();

    await()
        .atMost(ONE_MINUTE)
        .untilAsserted(
            () -> {
              assertThat(operator.get(ConfigMap.class, RESOURCE_NAME)).isNull();
              assertThat(operator.get(WorkflowAllFeatureCustomResource.class, RESOURCE_NAME))
                  .isNull();
            });
  }

  private void markConfigMapForDelete() {
    var cm = operator.get(ConfigMap.class, RESOURCE_NAME);
    if (cm.getMetadata().getAnnotations() == null) {
      cm.getMetadata().setAnnotations(new HashMap<>());
    }
    cm.getMetadata().getAnnotations().put(READY_TO_DELETE_ANNOTATION, "true");
    operator.replace(cm);
  }

  private WorkflowAllFeatureCustomResource customResource(boolean createConfigMap) {
    var res = new WorkflowAllFeatureCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    res.setSpec(new WorkflowAllFeatureSpec());
    res.getSpec().setCreateConfigMap(createConfigMap);
    return res;
  }
}

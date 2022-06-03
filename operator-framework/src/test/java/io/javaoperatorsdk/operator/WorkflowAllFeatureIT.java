package io.javaoperatorsdk.operator;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.workflowallfeature.WorkflowAllFeatureCustomResource;
import io.javaoperatorsdk.operator.sample.workflowallfeature.WorkflowAllFeatureReconciler;
import io.javaoperatorsdk.operator.sample.workflowallfeature.WorkflowAllFeatureSpec;

import static io.javaoperatorsdk.operator.sample.workflowallfeature.ConfigMapDependentResource.READY_TO_DELETE_ANNOTATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class WorkflowAllFeatureIT {

  public static final String RESOURCE_NAME = "test";
  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(WorkflowAllFeatureReconciler.class)
          .build();

  @Disabled
  @Test
  void configMapNotReconciledUntilDeploymentNotReady() {
    operator.create(WorkflowAllFeatureCustomResource.class, customResource(true));
    await()
        .pollInterval(Duration.ofMillis(20))
        .untilAsserted(
            () -> {
              assertThat(
                  operator
                      .getReconcilerOfType(WorkflowAllFeatureReconciler.class)
                      .getNumberOfReconciliationExecution())
                  .isPositive();
            });
    assertThat(operator.get(Deployment.class, RESOURCE_NAME)).isNotNull();
    assertThat(operator.get(ConfigMap.class, RESOURCE_NAME)).isNull();

    await().atMost(Duration.ofSeconds(230)).untilAsserted(() -> {
      assertThat(operator.get(ConfigMap.class, RESOURCE_NAME)).isNotNull();
    });
    markConfigMapForDelete();
  }

  // @Test
  void configMapNotReconciledIfReconcileConditionNotMet() {

  }

  // @Test
  void configMapNotDeletedUntilNotMarked() {

  }

  private void markConfigMapForDelete() {
    var cm = operator.get(ConfigMap.class, RESOURCE_NAME);
    cm.getMetadata().setAnnotations(Map.of(READY_TO_DELETE_ANNOTATION, "true"));
    operator.replace(ConfigMap.class, cm);
  }

  private WorkflowAllFeatureCustomResource customResource(boolean createConfigMap) {
    var res = new WorkflowAllFeatureCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName(RESOURCE_NAME)
        .build());
    res.setSpec(new WorkflowAllFeatureSpec());
    res.getSpec().setCreateConfigMap(createConfigMap);
    return res;
  }

}

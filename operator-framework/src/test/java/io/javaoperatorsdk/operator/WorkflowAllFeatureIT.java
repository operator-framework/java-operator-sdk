package io.javaoperatorsdk.operator;

import java.time.Duration;
import java.util.HashMap;

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
  private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(WorkflowAllFeatureReconciler.class)
          .build();

  @Test
  void configMapNotReconciledUntilDeploymentReady() {
    operator.create(customResource(true));
    await().untilAsserted(
        () -> {
          assertThat(operator
              .getReconcilerOfType(WorkflowAllFeatureReconciler.class)
              .getNumberOfReconciliationExecution())
              .isPositive();
          assertThat(operator.get(Deployment.class, RESOURCE_NAME)).isNotNull();
          assertThat(operator.get(ConfigMap.class, RESOURCE_NAME)).isNull();
        });

    await().atMost(ONE_MINUTE).untilAsserted(() -> {
      assertThat(operator
          .getReconcilerOfType(WorkflowAllFeatureReconciler.class)
          .getNumberOfReconciliationExecution())
          .isGreaterThan(1);
      assertThat(operator.get(ConfigMap.class, RESOURCE_NAME)).isNotNull();
      assertThat(operator.get(WorkflowAllFeatureCustomResource.class, RESOURCE_NAME)
          .getStatus().getReady()).isTrue();
    });

    markConfigMapForDelete();
  }


  @Test
  void configMapNotReconciledIfReconcileConditionNotMet() {
    var resource = operator.create(customResource(false));

    await().atMost(ONE_MINUTE).untilAsserted(() -> {
      assertThat(operator.get(ConfigMap.class, RESOURCE_NAME)).isNull();
      assertThat(operator.get(WorkflowAllFeatureCustomResource.class, RESOURCE_NAME)
          .getStatus().getReady()).isTrue();
    });

    resource.getSpec().setCreateConfigMap(true);
    operator.replace(resource);

    await().untilAsserted(() -> {
      assertThat(operator.get(ConfigMap.class, RESOURCE_NAME)).isNotNull();
      assertThat(operator.get(WorkflowAllFeatureCustomResource.class, RESOURCE_NAME)
          .getStatus().getReady()).isTrue();
    });
  }


  @Test
  void configMapNotDeletedUntilNotMarked() {
    var resource = operator.create(customResource(true));

    await().atMost(ONE_MINUTE).untilAsserted(() -> {
      assertThat(operator.get(WorkflowAllFeatureCustomResource.class, RESOURCE_NAME).getStatus())
          .isNotNull();
      assertThat(operator.get(WorkflowAllFeatureCustomResource.class, RESOURCE_NAME)
          .getStatus().getReady()).isTrue();
      assertThat(operator.get(ConfigMap.class, RESOURCE_NAME)).isNotNull();
    });

    operator.delete(resource);

    await().pollDelay(Duration.ofMillis(300)).untilAsserted(() -> {
      assertThat(operator.get(ConfigMap.class, RESOURCE_NAME)).isNotNull();
      assertThat(operator.get(WorkflowAllFeatureCustomResource.class, RESOURCE_NAME)).isNotNull();
    });

    markConfigMapForDelete();

    await().atMost(ONE_MINUTE).untilAsserted(() -> {
      assertThat(operator.get(ConfigMap.class, RESOURCE_NAME)).isNull();
      assertThat(operator.get(WorkflowAllFeatureCustomResource.class, RESOURCE_NAME)).isNull();
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
    res.setMetadata(new ObjectMetaBuilder()
        .withName(RESOURCE_NAME)
        .build());
    res.setSpec(new WorkflowAllFeatureSpec());
    res.getSpec().setCreateConfigMap(createConfigMap);
    return res;
  }

}

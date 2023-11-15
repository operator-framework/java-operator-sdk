package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.workflowactivationcondition.WorkflowActivationConditionCustomResource;
import io.javaoperatorsdk.operator.sample.workflowactivationcondition.WorkflowActivationConditionReconciler;
import io.javaoperatorsdk.operator.sample.workflowactivationcondition.WorkflowActivationConditionSpec;

import static io.javaoperatorsdk.operator.sample.workflowactivationcondition.ConfigMapDependentResource.DATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class WorkflowActivationConditionIT {

  public static final String TEST_RESOURCE_NAME = "test1";
  public static final String TEST_DATA = "test data";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(WorkflowActivationConditionReconciler.class)
          .build();

  @Test
  void routeIsNotCreated() {
    extension.create(testResource());

    await().untilAsserted(() -> {
      var cm = extension.get(ConfigMap.class, TEST_RESOURCE_NAME);
      assertThat(cm).isNotNull();
      assertThat(cm.getData()).containsEntry(DATA_KEY, TEST_DATA);
    });
  }

  @Test
  WorkflowActivationConditionCustomResource testResource() {
    var res = new WorkflowActivationConditionCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName(TEST_RESOURCE_NAME)
        .build());
    res.setSpec(new WorkflowActivationConditionSpec());
    res.getSpec().setValue(TEST_DATA);
    return res;
  }

}

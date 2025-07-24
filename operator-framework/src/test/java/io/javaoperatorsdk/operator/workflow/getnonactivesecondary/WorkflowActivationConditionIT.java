package io.javaoperatorsdk.operator.workflow.getnonactivesecondary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class WorkflowActivationConditionIT {

  public static final String TEST_RESOURCE_NAME = "test1";

  @RegisterExtension
  static LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(WorkflowActivationConditionReconciler.class)
          .build();

  @Test
  void reconciledOnVanillaKubernetesDespiteRouteInWorkflow() {
    extension.create(testResource());

    await()
        .untilAsserted(
            () -> {
              assertThat(
                      extension
                          .getReconcilerOfType(WorkflowActivationConditionReconciler.class)
                          .getNumberOfReconciliationExecution())
                  .isEqualTo(1);
            });
  }

  private GetNonActiveSecondaryCustomResource testResource() {
    var res = new GetNonActiveSecondaryCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    return res;
  }
}

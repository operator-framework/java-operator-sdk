package io.javaoperatorsdk.operator.workflow.getnonactivesecondary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Workflow Functions on Vanilla Kubernetes Despite Inactive Resources",
    description =
        """
        Verifies that workflows function correctly on vanilla Kubernetes even when they include \
        resources that are not available on the platform (like OpenShift Routes). The operator \
        successfully reconciles by skipping inactive dependents based on activation conditions, \
        demonstrating platform-agnostic operator design.
        """)
public class WorkflowActivationConditionIT {

  public static final String TEST_RESOURCE_NAME = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
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

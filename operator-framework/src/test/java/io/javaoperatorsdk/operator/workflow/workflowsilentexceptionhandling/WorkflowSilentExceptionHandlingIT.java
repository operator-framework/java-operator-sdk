package io.javaoperatorsdk.operator.workflow.workflowsilentexceptionhandling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class WorkflowSilentExceptionHandlingIT {

  @RegisterExtension
  static LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(HandleWorkflowExceptionsInReconcilerReconciler.class)
          .build();

  @Test
  void handleExceptionsInReconciler() {
    extension.create(testResource());
    var reconciler =
        extension.getReconcilerOfType(HandleWorkflowExceptionsInReconcilerReconciler.class);

    await()
        .untilAsserted(
            () -> {
              assertThat(reconciler.isErrorsFoundInReconcilerResult()).isTrue();
            });

    extension.delete(testResource());

    await()
        .untilAsserted(
            () -> {
              assertThat(reconciler.isErrorsFoundInCleanupResult()).isTrue();
            });
  }

  HandleWorkflowExceptionsInReconcilerCustomResource testResource() {
    var res = new HandleWorkflowExceptionsInReconcilerCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName("test1").build());
    return res;
  }
}

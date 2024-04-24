package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.workflowsilentexceptionhandling.WorkflowSilentExceptionHandlingCustomResource;
import io.javaoperatorsdk.operator.sample.workflowsilentexceptionhandling.WorkflowSilentExceptionHandlingReconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class WorkflowSilentExceptionHandlingIT {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(WorkflowSilentExceptionHandlingReconciler.class)
          .build();

  @Test
  void silentExceptionHandling() {
    extension.create(testResource());
    var reconciler = extension.getReconcilerOfType(WorkflowSilentExceptionHandlingReconciler.class);

    await().untilAsserted(() -> {
      assertThat(reconciler.isErrorsFoundInReconcilerResult()).isTrue();
    });

    extension.delete(testResource());

    await().untilAsserted(() -> {
      assertThat(reconciler.isErrorsFoundInCleanupResult()).isTrue();
    });
  }

  WorkflowSilentExceptionHandlingCustomResource testResource() {
    var res = new WorkflowSilentExceptionHandlingCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName("test1")
        .build());
    return res;
  }

}

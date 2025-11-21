package io.javaoperatorsdk.operator.workflow.workflowexplicitcleanup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Explicit Workflow Cleanup Invocation",
    description =
        """
        Tests explicit workflow cleanup invocation, demonstrating that workflow cleanup is called \
        even when using explicit workflow invocation mode. This ensures that dependent resources \
        are properly cleaned up during deletion regardless of how the workflow is invoked, \
        maintaining consistent cleanup behavior.
        """)
public class WorkflowExplicitCleanupIT {

  public static final String RESOURCE_NAME = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(WorkflowExplicitCleanupReconciler.class)
          .build();

  @Test
  void workflowInvokedExplicitly() {
    var res = extension.create(testResource());

    await()
        .untilAsserted(
            () -> {
              assertThat(extension.get(ConfigMap.class, RESOURCE_NAME)).isNotNull();
            });

    extension.delete(res);

    // The ConfigMap is not garbage collected, this tests that even if the cleaner is not
    // implemented the workflow cleanup still called even if there is explicit invocation
    await()
        .untilAsserted(
            () -> {
              assertThat(extension.get(ConfigMap.class, RESOURCE_NAME)).isNull();
            });
  }

  WorkflowExplicitCleanupCustomResource testResource() {
    var res = new WorkflowExplicitCleanupCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    return res;
  }
}

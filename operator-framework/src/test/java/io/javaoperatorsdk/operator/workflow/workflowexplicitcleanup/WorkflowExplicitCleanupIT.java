package io.javaoperatorsdk.operator.workflow.workflowexplicitcleanup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class WorkflowExplicitCleanupIT {

  public static final String RESOURCE_NAME = "test1";

  @RegisterExtension
  static LocallyRunOperatorExtension extension =
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

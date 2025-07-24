package io.javaoperatorsdk.operator.workflow.workflowexplicitinvocation;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class WorkflowExplicitInvocationIT {

  public static final String RESOURCE_NAME = "test1";

  @RegisterExtension
  static LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(WorkflowExplicitInvocationReconciler.class)
          .build();

  @Test
  void workflowInvokedExplicitly() {
    var res = extension.create(testResource());
    var reconciler = extension.getReconcilerOfType(WorkflowExplicitInvocationReconciler.class);

    await()
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions()).isEqualTo(1);
              assertThat(extension.get(ConfigMap.class, RESOURCE_NAME)).isNull();
            });

    reconciler.setInvokeWorkflow(true);

    // trigger reconciliation
    res.getSpec().setValue("changed value");
    res = extension.replace(res);

    await()
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions()).isEqualTo(2);
              assertThat(extension.get(ConfigMap.class, RESOURCE_NAME)).isNotNull();
            });

    extension.delete(res);

    // The ConfigMap is not garbage collected, this tests that even if the cleaner is not
    // implemented the workflow cleanup still called even if there is explicit invocation
    await()
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(extension.get(ConfigMap.class, RESOURCE_NAME)).isNull();
            });
  }

  WorkflowExplicitInvocationCustomResource testResource() {
    var res = new WorkflowExplicitInvocationCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    res.setSpec(new WorkflowExplicitInvocationSpec());
    res.getSpec().setValue("initial value");
    return res;
  }
}

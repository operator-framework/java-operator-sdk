package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.dependent.workflow.builder.WorkflowBuilder;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult.resourceCreated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowTest {

  public static final String VALUE = "value";
  private List<DependentResource<?, ?>> dependentResourceExecutions =
      Collections.synchronizedList(new ArrayList<>());

  @Test
  void reconcileTopLevelResources() {
    var dr1 = new TestDependent();
    var dr2 = new TestDependent();
    Workflow<TestCustomResource> workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(dr2).build()
        .build();

    workflow.reconcile(new TestCustomResource(), null);

    assertThat(dependentResourceExecutions).hasSize(2);
  }

  @Test
  void reconciliationWithSimpleDependsOn() {

  }

  @Test
  void reconciliationWithTheDependsOns() {

  }

  @Test
  void diamondShareWorkflowReconcile() {

  }

  private class TestDependent implements DependentResource<String, TestCustomResource> {
    @Override
    public ReconcileResult<String> reconcile(TestCustomResource primary,
        Context<TestCustomResource> context) {
      dependentResourceExecutions.add(this);
      return ReconcileResult.resourceCreated(VALUE);
    }

    @Override
    public Class<String> resourceType() {
      return String.class;
    }

    @Override
    public Optional<String> getSecondaryResource(TestCustomResource primary) {
      return Optional.of(VALUE);
    }
  }

}

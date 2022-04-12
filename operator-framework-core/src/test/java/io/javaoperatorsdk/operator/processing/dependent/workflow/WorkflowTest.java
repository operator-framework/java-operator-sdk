package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.AggregatedOperatorException;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.dependent.workflow.builder.WorkflowBuilder;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.ExecutionAssert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowTest {

  public static final String VALUE = "value";
  private List<DependentResource<?, ?>> executionHistory =
      Collections.synchronizedList(new ArrayList<>());

  TestDependent dr1 = new TestDependent("DR_1");
  TestDependent dr2 = new TestDependent("DR_2");
  TestErrorDependent errorDR2 = new TestErrorDependent("ERROR_1");

  @Test
  void reconcileTopLevelResources() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(dr2).build()
        .build();

    workflow.reconcile(new TestCustomResource(), null);

    assertThat(executionHistory).reconciled(dr1, dr2);
  }

  @Test
  void reconciliationWithSimpleDependsOn() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(dr2).dependsOn(dr1).build()
        .build();

    workflow.reconcile(new TestCustomResource(), null);

    assertThat(executionHistory).reconciledInOrder(dr1, dr2);
  }

  @Test
  void reconciliationWithTwoTheDependsOns() {
    TestDependent dr3 = new TestDependent("DR_3");

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(dr2).dependsOn(dr1).build()
        .addDependent(dr3).dependsOn(dr1).build()
        .build();

    workflow.reconcile(new TestCustomResource(), null);

    assertThat(executionHistory)
        .reconciledInOrder(dr1, dr2).reconciledInOrder(dr1, dr3);
  }

  @Test
  void diamondShareWorkflowReconcile() {
    TestDependent dr3 = new TestDependent("DR_3");
    TestDependent dr4 = new TestDependent("DR_4");

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(dr2).dependsOn(dr1).build()
        .addDependent(dr3).dependsOn(dr1).build()
        .addDependent(dr4).dependsOn(dr3).dependsOn(dr2).build()
        .build();

    workflow.reconcile(new TestCustomResource(), null);

    assertThat(executionHistory)
        .reconciledInOrder(dr1, dr2, dr4)
        .reconciledInOrder(dr1, dr3, dr4);
  }

  @Test
  void exceptionHandlingSimpleCases() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(errorDR2).build()
        .build();
    assertThrows(AggregatedOperatorException.class,
        () -> workflow.reconcile(new TestCustomResource(), null));
    assertThat(executionHistory).notReconciled(errorDR2);
  }

  @Test
  void dependentsOnErroredResourceNotReconciled() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(errorDR2).dependsOn(dr1).build()
        .addDependent(dr2).dependsOn(errorDR2).build()
        .build();
    assertThrows(AggregatedOperatorException.class,
        () -> workflow.reconcile(new TestCustomResource(), null));

    assertThat(executionHistory).reconciled(dr1).notReconciled(errorDR2, dr2);
  }

  @Test
  void onlyOneDependsOnErroredResourceNotReconciled() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(errorDR2).build()
        .addDependent(dr2).dependsOn(errorDR2).dependsOn(dr1).build()
        .build();
    assertThrows(AggregatedOperatorException.class,
        () -> workflow.reconcile(new TestCustomResource(), null));

    assertThat(executionHistory).notReconciled(dr2);
  }

  private class TestDependent implements DependentResource<String, TestCustomResource> {

    private String name;

    public TestDependent(String name) {
      this.name = name;
    }

    @Override
    public ReconcileResult<String> reconcile(TestCustomResource primary,
        Context<TestCustomResource> context) {
      executionHistory.add(this);
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

    @Override
    public String toString() {
      return name;
    }
  }

  private class TestErrorDependent implements DependentResource<String, TestCustomResource> {
    private String name;

    public TestErrorDependent(String name) {
      this.name = name;
    }

    @Override
    public ReconcileResult<String> reconcile(TestCustomResource primary,
        Context<TestCustomResource> context) {
      executionHistory.add(this);
      throw new IllegalStateException("Test exception");
    }

    @Override
    public Class<String> resourceType() {
      return String.class;
    }

    @Override
    public Optional<String> getSecondaryResource(TestCustomResource primary) {
      return Optional.of(VALUE);
    }

    @Override
    public String toString() {
      return name;
    }
  }

}

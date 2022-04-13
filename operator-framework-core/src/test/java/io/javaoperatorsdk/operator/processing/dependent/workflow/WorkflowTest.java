package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.AggregatedOperatorException;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.dependent.workflow.builder.WorkflowBuilder;
import io.javaoperatorsdk.operator.processing.dependent.workflow.condition.ReconcileCondition;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.ExecutionAssert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowTest {

  private ReconcileCondition met_reconcile_condition =
      (dependentResource, primary, context) -> true;
  private ReconcileCondition not_met_reconcile_condition =
      (dependentResource, primary, context) -> false;

  public static final String VALUE = "value";
  private List<ReconcileRecord> executionHistory =
      Collections.synchronizedList(new ArrayList<>());

  TestDependent dr1 = new TestDependent("DR_1");
  TestDependent dr2 = new TestDependent("DR_2");
  TestDeleterDependent drDeleter = new TestDeleterDependent("DR_DELETER");
  TestErrorDependent drError = new TestErrorDependent("ERROR_1");

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
        .addDependent(drError).build()
        .build();
    assertThrows(AggregatedOperatorException.class,
        () -> workflow.reconcile(new TestCustomResource(), null));
    assertThat(executionHistory).notReconciled(drError);
  }

  @Test
  void dependentsOnErroredResourceNotReconciled() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(drError).dependsOn(dr1).build()
        .addDependent(dr2).dependsOn(drError).build()
        .build();
    assertThrows(AggregatedOperatorException.class,
        () -> workflow.reconcile(new TestCustomResource(), null));

    assertThat(executionHistory).reconciled(dr1).notReconciled(drError, dr2);
  }

  @Test
  void onlyOneDependsOnErroredResourceNotReconciled() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(drError).build()
        .addDependent(dr2).dependsOn(drError).dependsOn(dr1).build()
        .build();
    assertThrows(AggregatedOperatorException.class,
        () -> workflow.reconcile(new TestCustomResource(), null));

    assertThat(executionHistory).notReconciled(dr2);
  }

  @Test
  void simpleReconcileCondition() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).withReconcileCondition(not_met_reconcile_condition).build()
        .addDependent(dr2).withReconcileCondition(met_reconcile_condition).build()
        .addDependent(drDeleter).withReconcileCondition(not_met_reconcile_condition).build()
        .build();

    workflow.reconcile(new TestCustomResource(), null);

    assertThat(executionHistory).notReconciled(dr1);
    assertThat(executionHistory).reconciled(dr2);
    assertThat(executionHistory).deleted(drDeleter);
  }

  @Test
  void reconcileConditionTransitiveDelete() {
    TestDeleterDependent drDeleter2 = new TestDeleterDependent("DR_DELETER_2");

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(dr2).withReconcileCondition(not_met_reconcile_condition).dependsOn(dr1)
        .build()
        .addDependent(drDeleter).withReconcileCondition(met_reconcile_condition).dependsOn(dr2)
        .build()
        .addDependent(drDeleter2).withReconcileCondition(met_reconcile_condition)
        .dependsOn(drDeleter).build()
        .build();

    workflow.reconcile(new TestCustomResource(), null);

    assertThat(executionHistory).notReconciled(dr2);
    assertThat(executionHistory).reconciledInOrder(dr1, drDeleter, drDeleter2);
    assertThat(executionHistory).deleted(drDeleter, drDeleter2);
  }

  @Test
  void reconcileConditionAlsoErrorDependsOn() {
    TestDeleterDependent drDeleter2 = new TestDeleterDependent("DR_DELETER_2");

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(drError).build()
        .addDependent(drDeleter).withReconcileCondition(not_met_reconcile_condition).build()
        .addDependent(drDeleter2).withReconcileCondition(met_reconcile_condition)
        .dependsOn(drError, drDeleter).build()
        .build();

    assertThrows(AggregatedOperatorException.class,
        () -> workflow.reconcile(new TestCustomResource(), null));

    assertThat(executionHistory).deleted(drDeleter);
    assertThat(executionHistory).reconciled(drError);
    assertThat(executionHistory).notReconciled(drDeleter2);
  }

  @Test
  void oneDependsOnConditionNotMet() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(dr2).withReconcileCondition(not_met_reconcile_condition).build()
        .addDependent(drDeleter).dependsOn(dr1, dr2).build()
        .build();

    workflow.reconcile(new TestCustomResource(), null);

    assertThat(executionHistory).deleted(drDeleter);
    assertThat(executionHistory).reconciledInOrder(dr1, drDeleter);
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
      executionHistory.add(new ReconcileRecord(this));
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

  private class TestDeleterDependent extends TestDependent implements Deleter<TestCustomResource> {

    public TestDeleterDependent(String name) {
      super(name);
    }

    @Override
    public void delete(TestCustomResource primary, Context<TestCustomResource> context) {
      executionHistory.add(new ReconcileRecord(this, true));
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
      executionHistory.add(new ReconcileRecord(this));
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

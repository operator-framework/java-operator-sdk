package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.AggregatedOperatorException;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.dependent.workflow.builder.WorkflowBuilder;
import io.javaoperatorsdk.operator.processing.dependent.workflow.condition.ReadyCondition;
import io.javaoperatorsdk.operator.processing.dependent.workflow.condition.ReconcileCondition;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.ExecutionAssert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowTest {

  public static final String NOT_READY_YET = "NOT READY YET";
  private ReconcileCondition met_reconcile_condition =
      (dependentResource, primary, context) -> true;
  private ReconcileCondition not_met_reconcile_condition =
      (dependentResource, primary, context) -> false;

  private ReadyCondition<String, TestCustomResource> metReadyCondition =
      (dependentResource, primary, context) -> true;
  private ReadyCondition<String, TestCustomResource> notMetReadyCondition =
      (dependentResource, primary, context) -> false;

  private ReadyCondition<String, TestCustomResource> notMetReadyConditionWithStatusUpdate =
      new ReadyCondition<>() {
        @Override
        public boolean isMet(DependentResource<String, TestCustomResource> dependentResource,
            TestCustomResource primary, Context<TestCustomResource> context) {
          return false;
        }

        @Override
        public void addNotReadyStatusInfo(TestCustomResource primary) {
          primary.getStatus().setConfigMapStatus(NOT_READY_YET);
        }
      };

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
    assertThat(executionHistory).reconciled(drError);
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

    assertThat(executionHistory).reconciled(dr1, drError).notReconciled(dr2);
  }

  @Test
  void oneBranchErrorsOtherCompletes() {
    TestDependent dr3 = new TestDependent("DR_3");

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(drError).dependsOn(dr1).build()
        .addDependent(dr2).dependsOn(dr1).build()
        .addDependent(dr3).dependsOn(dr2).build()
        .build();

    assertThrows(AggregatedOperatorException.class,
        () -> workflow.reconcile(new TestCustomResource(), null));

    assertThat(executionHistory).reconciledInOrder(dr1, dr2, dr3);
    assertThat(executionHistory).reconciledInOrder(dr1, drError);
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

    assertThat(executionHistory).notReconciled(dr1).reconciled(dr2).deleted(drDeleter);

  }

  @Test
  void triangleOnceConditionNotMet() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(dr2).dependsOn(dr1).build()
        .addDependent(drDeleter).withReconcileCondition(not_met_reconcile_condition).dependsOn(dr1)
        .build()
        .build();

    workflow.reconcile(new TestCustomResource(), null);

    assertThat(executionHistory).reconciledInOrder(dr1, dr2);
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

  @Test
  void readyConditionTrivialCase() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).withReadyCondition(metReadyCondition).build()
        .addDependent(dr2).dependsOn(dr1).build()
        .build();

    workflow.reconcile(new TestCustomResource(), null);

    assertThat(executionHistory).reconciledInOrder(dr1, dr2);
  }

  @Test
  void readyConditionNotMetTrivialCase() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).withReadyCondition(notMetReadyCondition).build()
        .addDependent(dr2).dependsOn(dr1).build()
        .build();

    workflow.reconcile(new TestCustomResource(), null);

    assertThat(executionHistory).reconciled(dr1).notReconciled(dr2);
  }

  @Test
  void readyConditionNotMetStatusUpdates() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).withReadyCondition(notMetReadyConditionWithStatusUpdate).build()
        .addDependent(dr2).dependsOn(dr1).build()
        .build();

    var cr = new TestCustomResource();
    workflow.reconcile(cr, null);

    assertThat(executionHistory).reconciled(dr1).notReconciled(dr2);
    Assertions.assertThat(cr.getStatus().getConfigMapStatus()).isEqualTo(NOT_READY_YET);
  }

  @Test
  void readyConditionNotMetInOneParent() {
    TestDependent dr3 = new TestDependent("DR_3");

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).withReadyCondition(notMetReadyCondition).build()
        .addDependent(dr2).build()
        .addDependent(dr3).dependsOn(dr1, dr2).build()
        .build();

    workflow.reconcile(new TestCustomResource(), null);

    assertThat(executionHistory).reconciled(dr1, dr2).notReconciled(dr3);
  }

  @Test
  void diamondShareWithReadyCondition() {
    TestDependent dr3 = new TestDependent("DR_3");
    TestDependent dr4 = new TestDependent("DR_4");

    var workflow = new WorkflowBuilder<TestCustomResource>()
            .addDependent(dr1).build()
            .addDependent(dr2).dependsOn(dr1).withReadyCondition(notMetReadyCondition).build()
            .addDependent(dr3).dependsOn(dr1).build()
            .addDependent(dr4).dependsOn(dr2,dr3).build()
            .build();

    workflow.reconcile(new TestCustomResource(), null);

    assertThat(executionHistory).reconciledInOrder(dr1, dr2)
            .reconciledInOrder(dr1, dr3)
            .notReconciled(dr4);
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

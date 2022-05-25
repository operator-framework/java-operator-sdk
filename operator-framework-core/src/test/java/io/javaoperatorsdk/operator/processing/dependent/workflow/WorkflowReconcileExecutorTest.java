package io.javaoperatorsdk.operator.processing.dependent.workflow;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.AggregatedOperatorException;
import io.javaoperatorsdk.operator.processing.dependent.workflow.builder.WorkflowBuilder;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.ExecutionAssert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowReconcileExecutorTest extends AbstractWorkflowExecutorTest {

  private Condition met_reconcile_condition =
      (dependentResource, primary, context) -> true;
  private Condition not_met_reconcile_condition =
      (dependentResource, primary, context) -> false;

  private Condition<String, TestCustomResource> metReadyCondition =
      (dependentResource, primary, context) -> true;
  private Condition<String, TestCustomResource> notMetReadyCondition =
      (dependentResource, primary, context) -> false;

  private Condition<String, TestCustomResource> notMetReadyConditionWithStatusUpdate =
      (dependentResource, primary, context) -> false;

  @Test
  void reconcileTopLevelResources() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(dr2).build()
        .build();

    var res = workflow.reconcile(new TestCustomResource(), null);

    assertThat(executionHistory).reconciled(dr1, dr2);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1, dr2);
  }

  @Test
  void reconciliationWithSimpleDependsOn() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(dr2).dependsOn(dr1).build()
        .build();

    var res = workflow.reconcile(new TestCustomResource(), null);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    assertThat(executionHistory).reconciledInOrder(dr1, dr2);
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1, dr2);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void reconciliationWithTwoTheDependsOns() {
    TestDependent dr3 = new TestDependent("DR_3");

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(dr2).dependsOn(dr1).build()
        .addDependent(dr3).dependsOn(dr1).build()
        .build();

    var res = workflow.reconcile(new TestCustomResource(), null);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    assertThat(executionHistory)
        .reconciledInOrder(dr1, dr2).reconciledInOrder(dr1, dr3);
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1, dr2, dr3);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
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

    var res = workflow.reconcile(new TestCustomResource(), null);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    assertThat(executionHistory)
        .reconciledInOrder(dr1, dr2, dr4)
        .reconciledInOrder(dr1, dr3, dr4);

    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1, dr2, dr3,
        dr4);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void exceptionHandlingSimpleCases() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(drError).build()
        .withThrowExceptionFurther(false)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), null);

    assertThrows(AggregatedOperatorException.class,
        res::throwAggregateExceptionIfErrorsPresent);

    assertThat(executionHistory).reconciled(drError);
    Assertions.assertThat(res.getErroredDependents()).containsOnlyKeys(drError);
    Assertions.assertThat(res.getReconciledDependents()).isEmpty();
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void dependentsOnErroredResourceNotReconciled() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(drError).dependsOn(dr1).build()
        .addDependent(dr2).dependsOn(drError).build()
        .withThrowExceptionFurther(false)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), null);
    assertThrows(AggregatedOperatorException.class,
        res::throwAggregateExceptionIfErrorsPresent);

    assertThat(executionHistory).reconciled(dr1, drError).notReconciled(dr2);
    Assertions.assertThat(res.getErroredDependents()).containsOnlyKeys(drError);
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1);
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void oneBranchErrorsOtherCompletes() {
    TestDependent dr3 = new TestDependent("DR_3");

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(drError).dependsOn(dr1).build()
        .addDependent(dr2).dependsOn(dr1).build()
        .addDependent(dr3).dependsOn(dr2).build()
        .withThrowExceptionFurther(false)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), null);
    assertThrows(AggregatedOperatorException.class,
        res::throwAggregateExceptionIfErrorsPresent);

    assertThat(executionHistory).reconciledInOrder(dr1, dr2, dr3).reconciledInOrder(dr1, drError);
    Assertions.assertThat(res.getErroredDependents()).containsOnlyKeys(drError);
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1, dr2, dr3);
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void onlyOneDependsOnErroredResourceNotReconciled() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(drError).build()
        .addDependent(dr2).dependsOn(drError, dr1).build()
        .withThrowExceptionFurther(false)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), null);
    assertThrows(AggregatedOperatorException.class,
        res::throwAggregateExceptionIfErrorsPresent);

    assertThat(executionHistory).notReconciled(dr2);
    Assertions.assertThat(res.getErroredDependents()).containsKey(drError);
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1);
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void simpleReconcileCondition() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).withReconcileCondition(not_met_reconcile_condition).build()
        .addDependent(dr2).withReconcileCondition(met_reconcile_condition).build()
        .addDependent(drDeleter).withReconcileCondition(not_met_reconcile_condition).build()
        .build();

    var res = workflow.reconcile(new TestCustomResource(), null);

    assertThat(executionHistory).notReconciled(dr1).reconciled(dr2).deleted(drDeleter);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr2);
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }


  @Test
  void triangleOnceConditionNotMet() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(dr2).dependsOn(dr1).build()
        .addDependent(drDeleter).withReconcileCondition(not_met_reconcile_condition).dependsOn(dr1)
        .build()
        .build();

    var res = workflow.reconcile(new TestCustomResource(), null);

    assertThat(executionHistory).reconciledInOrder(dr1, dr2).deleted(drDeleter);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1, dr2);
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void reconcileConditionTransitiveDelete() {
    TestDeleterDependent drDeleter2 = new TestDeleterDependent("DR_DELETER_2");

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(dr2).dependsOn(dr1).withReconcileCondition(not_met_reconcile_condition)
        .build()
        .addDependent(drDeleter).dependsOn(dr2).withReconcileCondition(met_reconcile_condition)
        .build()
        .addDependent(drDeleter2).dependsOn(drDeleter)
        .withReconcileCondition(met_reconcile_condition).build()
        .build();

    var res = workflow.reconcile(new TestCustomResource(), null);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    assertThat(executionHistory).notReconciled(dr2);
    assertThat(executionHistory).reconciledInOrder(dr1, drDeleter2, drDeleter);
    assertThat(executionHistory).deleted(drDeleter2, drDeleter);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1);
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void reconcileConditionAlsoErrorDependsOn() {
    TestDeleterDependent drDeleter2 = new TestDeleterDependent("DR_DELETER_2");

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(drError).build()
        .addDependent(drDeleter).withReconcileCondition(not_met_reconcile_condition).build()
        .addDependent(drDeleter2).dependsOn(drError, drDeleter)
        .withReconcileCondition(met_reconcile_condition)
        .build()
        .withThrowExceptionFurther(false)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), null);
    assertThrows(AggregatedOperatorException.class,
        res::throwAggregateExceptionIfErrorsPresent);

    assertThat(executionHistory)
        .deleted(drDeleter2, drDeleter)
        .reconciled(drError);

    Assertions.assertThat(res.getErroredDependents()).containsOnlyKeys(drError);
    Assertions.assertThat(res.getReconciledDependents()).isEmpty();
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void oneDependsOnConditionNotMet() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(dr2).withReconcileCondition(not_met_reconcile_condition).build()
        .addDependent(drDeleter).dependsOn(dr1, dr2).build()
        .build();

    var res = workflow.reconcile(new TestCustomResource(), null);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();

    assertThat(executionHistory).deleted(drDeleter).notReconciled(dr2).reconciled(dr1);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1);
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void deletedIfReconcileConditionNotMet() {
    TestDeleterDependent drDeleter2 = new TestDeleterDependent("DR_DELETER_2");
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(drDeleter).dependsOn(dr1).withReconcileCondition(not_met_reconcile_condition)
        .build()
        .addDependent(drDeleter2).dependsOn(dr1, drDeleter).build()
        .build();

    var res = workflow.reconcile(new TestCustomResource(), null);

    assertThat(executionHistory)
        .reconciledInOrder(dr1, drDeleter2, drDeleter)
        .deleted(drDeleter2, drDeleter);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1);
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void deleteDoneInReverseOrder() {
    TestDeleterDependent drDeleter2 = new TestDeleterDependent("DR_DELETER_2");
    TestDeleterDependent drDeleter3 = new TestDeleterDependent("DR_DELETER_3");
    TestDeleterDependent drDeleter4 = new TestDeleterDependent("DR_DELETER_4");

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(drDeleter).withReconcileCondition(not_met_reconcile_condition).dependsOn(dr1)
        .build()
        .addDependent(drDeleter2).dependsOn(drDeleter).build()
        .addDependent(drDeleter3).dependsOn(drDeleter).build()
        .addDependent(drDeleter4).dependsOn(drDeleter3).build()
        .build();

    var res = workflow.reconcile(new TestCustomResource(), null);

    assertThat(executionHistory)
        .reconciledInOrder(dr1, drDeleter4, drDeleter3, drDeleter)
        .reconciledInOrder(dr1, drDeleter2, drDeleter)
        .deleted(drDeleter, drDeleter2, drDeleter3, drDeleter4);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1);
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void diamondDeleteWithPostConditionInMiddle() {
    TestDeleterDependent drDeleter2 = new TestDeleterDependent("DR_DELETER_2");
    TestDeleterDependent drDeleter3 = new TestDeleterDependent("DR_DELETER_3");
    TestDeleterDependent drDeleter4 = new TestDeleterDependent("DR_DELETER_4");

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(drDeleter).withReconcileCondition(not_met_reconcile_condition).build()
        .addDependent(drDeleter2).dependsOn(drDeleter).build()
        .addDependent(drDeleter3).dependsOn(drDeleter)
        .withDeletePostCondition(noMetDeletePostCondition).build()
        .addDependent(drDeleter4).dependsOn(drDeleter3, drDeleter2).build()
        .build();

    var res = workflow.reconcile(new TestCustomResource(), null);

    assertThat(executionHistory).notReconciled(drDeleter)
        .reconciledInOrder(drDeleter4, drDeleter2)
        .reconciledInOrder(drDeleter4, drDeleter3);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).isEmpty();
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void diamondDeleteErrorInMiddle() {
    TestDeleterDependent drDeleter2 = new TestDeleterDependent("DR_DELETER_2");
    TestDeleterDependent drDeleter3 = new TestDeleterDependent("DR_DELETER_3");

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(drDeleter).withReconcileCondition(not_met_reconcile_condition).build()
        .addDependent(drDeleter2).dependsOn(drDeleter).build()
        .addDependent(errorDD).dependsOn(drDeleter).build()
        .addDependent(drDeleter3).dependsOn(errorDD, drDeleter2).build()
        .withThrowExceptionFurther(false)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), null);

    assertThat(executionHistory)
        .notReconciled(drDeleter, drError)
        .reconciledInOrder(drDeleter3, drDeleter2);

    Assertions.assertThat(res.getErroredDependents()).containsOnlyKeys(errorDD);
    Assertions.assertThat(res.getReconciledDependents()).isEmpty();
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void readyConditionTrivialCase() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).withReadyCondition(metReadyCondition).build()
        .addDependent(dr2).dependsOn(dr1).build()
        .build();

    var res = workflow.reconcile(new TestCustomResource(), null);

    assertThat(executionHistory).reconciledInOrder(dr1, dr2);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1, dr2);
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void readyConditionNotMetTrivialCase() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).withReadyCondition(notMetReadyCondition).build()
        .addDependent(dr2).dependsOn(dr1).build()
        .build();

    var res = workflow.reconcile(new TestCustomResource(), null);


    assertThat(executionHistory).reconciled(dr1).notReconciled(dr2);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1);
    Assertions.assertThat(res.getNotReadyDependents()).containsExactlyInAnyOrder(dr1);
  }

  @Test
  void readyConditionNotMetInOneParent() {
    TestDependent dr3 = new TestDependent("DR_3");

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).withReadyCondition(notMetReadyCondition).build()
        .addDependent(dr2).build()
        .addDependent(dr3).dependsOn(dr1, dr2).build()
        .build();

    var res = workflow.reconcile(new TestCustomResource(), null);

    assertThat(executionHistory).reconciled(dr1, dr2).notReconciled(dr3);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1, dr2);
    Assertions.assertThat(res.getNotReadyDependents()).containsExactlyInAnyOrder(dr1);
  }

  @Test
  void diamondShareWithReadyCondition() {
    TestDependent dr3 = new TestDependent("DR_3");
    TestDependent dr4 = new TestDependent("DR_4");

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dr1).build()
        .addDependent(dr2).dependsOn(dr1).withReadyCondition(notMetReadyCondition).build()
        .addDependent(dr3).dependsOn(dr1).build()
        .addDependent(dr4).dependsOn(dr2, dr3).build()
        .build();

    var res = workflow.reconcile(new TestCustomResource(), null);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    assertThat(executionHistory).reconciledInOrder(dr1, dr2)
        .reconciledInOrder(dr1, dr3)
        .notReconciled(dr4);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1, dr2, dr3);
    Assertions.assertThat(res.getNotReadyDependents()).containsExactlyInAnyOrder(dr2);
  }

}

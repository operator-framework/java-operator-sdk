package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.AggregatedOperatorException;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.ExecutionAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@SuppressWarnings("rawtypes")
class WorkflowReconcileExecutorTest extends AbstractWorkflowExecutorTest {

  @SuppressWarnings("unchecked")
  Context<TestCustomResource> mockContext = mock(Context.class);
  ExecutorService executorService = Executors.newCachedThreadPool();

  TestDependent dr3 = new TestDependent("DR_3");
  TestDependent dr4 = new TestDependent("DR_4");

  @BeforeEach
  void setup() {
    when(mockContext.getWorkflowExecutorService()).thenReturn(executorService);
    when(mockContext.eventSourceRetriever()).thenReturn(mock(EventSourceRetriever.class));
  }

  @Test
  void reconcileTopLevelResources() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependentResource(dr1)
        .addDependentResource(dr2)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    assertThat(executionHistory).reconciled(dr1, dr2);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1, dr2);
  }

  @Test
  void reconciliationWithSimpleDependsOn() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependentResource(dr1)
        .addDependentResource(dr2).dependsOn(dr1)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    assertThat(executionHistory).reconciledInOrder(dr1, dr2);
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1, dr2);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void reconciliationWithTwoTheDependsOns() {

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependentResource(dr1)
        .addDependentResource(dr2).dependsOn(dr1)
        .addDependentResource(dr3).dependsOn(dr1)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    assertThat(executionHistory)
        .reconciledInOrder(dr1, dr2).reconciledInOrder(dr1, dr3);
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1, dr2, dr3);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void diamondShareWorkflowReconcile() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependentResource(dr1)
        .addDependentResource(dr2).dependsOn(dr1)
        .addDependentResource(dr3).dependsOn(dr1)
        .addDependentResource(dr4).dependsOn(dr3).dependsOn(dr2)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

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
        .addDependentResource(drError)
        .withThrowExceptionFurther(false)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

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
        .addDependentResource(dr1)
        .addDependentResource(drError).dependsOn(dr1)
        .addDependentResource(dr2).dependsOn(drError)
        .withThrowExceptionFurther(false)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);
    assertThrows(AggregatedOperatorException.class,
        res::throwAggregateExceptionIfErrorsPresent);

    assertThat(executionHistory).reconciled(dr1, drError).notReconciled(dr2);
    Assertions.assertThat(res.getErroredDependents()).containsOnlyKeys(drError);
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1);
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void oneBranchErrorsOtherCompletes() {

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependentResource(dr1)
        .addDependentResource(drError).dependsOn(dr1)
        .addDependentResource(dr2).dependsOn(dr1)
        .addDependentResource(dr3).dependsOn(dr2)
        .withThrowExceptionFurther(false)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);
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
        .addDependentResource(dr1)
        .addDependentResource(drError)
        .addDependentResource(dr2).dependsOn(drError, dr1)
        .withThrowExceptionFurther(false)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);
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
        .addDependentResource(dr1).withReconcilePrecondition(notMetCondition)
        .addDependentResource(dr2).withReconcilePrecondition(metCondition)
        .addDependentResource(drDeleter).withReconcilePrecondition(notMetCondition)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    assertThat(executionHistory).notReconciled(dr1).reconciled(dr2).deleted(drDeleter);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr2);
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }


  @Test
  void triangleOnceConditionNotMet() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependentResource(dr1)
        .addDependentResource(dr2).dependsOn(dr1)
        .addDependentResource(drDeleter).withReconcilePrecondition(notMetCondition)
        .dependsOn(dr1)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    assertThat(executionHistory).reconciledInOrder(dr1, dr2).deleted(drDeleter);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1, dr2);
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void reconcileConditionTransitiveDelete() {
    TestDeleterDependent drDeleter2 = new TestDeleterDependent("DR_DELETER_2");

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependentResource(dr1)
        .addDependentResource(dr2).dependsOn(dr1)
        .withReconcilePrecondition(notMetCondition)
        .addDependentResource(drDeleter).dependsOn(dr2)
        .withReconcilePrecondition(metCondition)
        .addDependentResource(drDeleter2).dependsOn(drDeleter)
        .withReconcilePrecondition(metCondition)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

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
        .addDependentResource(drError)
        .addDependentResource(drDeleter).withReconcilePrecondition(notMetCondition)
        .addDependentResource(drDeleter2).dependsOn(drError, drDeleter)
        .withReconcilePrecondition(metCondition)
        .withThrowExceptionFurther(false)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);
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
        .addDependentResource(dr1)
        .addDependentResource(dr2).withReconcilePrecondition(notMetCondition)
        .addDependentResource(drDeleter).dependsOn(dr1, dr2)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

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
        .addDependentResource(dr1)
        .addDependentResource(drDeleter).dependsOn(dr1)
        .withReconcilePrecondition(notMetCondition)
        .addDependentResource(drDeleter2).dependsOn(dr1, drDeleter)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

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
        .addDependentResource(dr1)
        .addDependentResource(drDeleter).withReconcilePrecondition(notMetCondition)
        .dependsOn(dr1)
        .addDependentResource(drDeleter2).dependsOn(drDeleter)
        .addDependentResource(drDeleter3).dependsOn(drDeleter)
        .addDependentResource(drDeleter4).dependsOn(drDeleter3)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

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
        .addDependentResource(drDeleter).withReconcilePrecondition(notMetCondition)
        .addDependentResource(drDeleter2).dependsOn(drDeleter)
        .addDependentResource(drDeleter3).dependsOn(drDeleter)
        .withDeletePostcondition(this.notMetCondition)
        .addDependentResource(drDeleter4).dependsOn(drDeleter3, drDeleter2)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

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
        .addDependentResource(drDeleter).withReconcilePrecondition(notMetCondition)
        .addDependentResource(drDeleter2).dependsOn(drDeleter)
        .addDependentResource(errorDD).dependsOn(drDeleter)
        .addDependentResource(drDeleter3).dependsOn(errorDD, drDeleter2)
        .withThrowExceptionFurther(false)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

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
        .addDependentResource(dr1).withReadyPostcondition(metCondition)
        .addDependentResource(dr2).dependsOn(dr1)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    assertThat(executionHistory).reconciledInOrder(dr1, dr2);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1, dr2);
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void readyConditionNotMetTrivialCase() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependentResource(dr1).withReadyPostcondition(notMetCondition)
        .addDependentResource(dr2).dependsOn(dr1)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);


    assertThat(executionHistory).reconciled(dr1).notReconciled(dr2);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1);
    Assertions.assertThat(res.getNotReadyDependents()).containsExactlyInAnyOrder(dr1);
  }

  @Test
  void readyConditionNotMetInOneParent() {

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependentResource(dr1).withReadyPostcondition(notMetCondition)
        .addDependentResource(dr2)
        .addDependentResource(dr3).dependsOn(dr1, dr2)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    assertThat(executionHistory).reconciled(dr1, dr2).notReconciled(dr3);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1, dr2);
    Assertions.assertThat(res.getNotReadyDependents()).containsExactlyInAnyOrder(dr1);
  }

  @Test
  void diamondShareWithReadyCondition() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependentResource(dr1)
        .addDependentResource(dr2).dependsOn(dr1).withReadyPostcondition(notMetCondition)
        .addDependentResource(dr3).dependsOn(dr1)
        .addDependentResource(dr4).dependsOn(dr2, dr3)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    assertThat(executionHistory).reconciledInOrder(dr1, dr2)
        .reconciledInOrder(dr1, dr3)
        .notReconciled(dr4);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1, dr2, dr3);
    Assertions.assertThat(res.getNotReadyDependents()).containsExactlyInAnyOrder(dr2);
  }

  @Test
  void garbageCollectedResourceIsDeletedIfReconcilePreconditionDoesNotHold() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependentResource(gcDeleter).withReconcilePrecondition(notMetCondition)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    assertThat(executionHistory).deleted(gcDeleter);
  }

  @Test
  void garbageCollectedDeepResourceIsDeletedIfReconcilePreconditionDoesNotHold() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependentResource(dr1).withReconcilePrecondition(notMetCondition)
        .addDependentResource(gcDeleter).dependsOn(dr1)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    assertThat(executionHistory).deleted(gcDeleter);
  }

  @Test
  void notReconciledIfActivationConditionNotMet() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependentResource(dr1)
        .withActivationCondition(notMetCondition)
        .addDependentResource(dr2)
        .build();
    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    assertThat(executionHistory).reconciled(dr2).notReconciled(dr1);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).contains(dr2);
  }

  @Test
  void dependentsOnANonActiveDependentNotReconciled() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependentResource(dr1)
        .withActivationCondition(notMetCondition)
        .addDependentResource(dr2)
        .addDependentResource(dr3).dependsOn(dr1)
        .build();
    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    assertThat(executionHistory).reconciled(dr2).notReconciled(dr1, dr3);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).contains(dr2);
  }

  @Test
  void readyConditionNotCheckedOnNonActiveDependent() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependentResource(dr1)
        .withActivationCondition(notMetCondition)
        .withReadyPostcondition(notMetCondition)
        .addDependentResource(dr2)
        .addDependentResource(dr3).dependsOn(dr1)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void reconcilePreconditionNotCheckedOnNonActiveDependent() {
    var precondition = mock(Condition.class);

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependentResource(dr1)
        .withActivationCondition(notMetCondition)
        .withReconcilePrecondition(precondition)
        .build();

    workflow.reconcile(new TestCustomResource(), mockContext);

    verify(precondition, never()).isMet(any(), any(), any());
  }

  @Test
  void deletesDependentsOfNonActiveDependentButNotTheNonActive() {
    TestDeleterDependent drDeleter2 = new TestDeleterDependent("DR_DELETER_2");
    TestDeleterDependent drDeleter3 = new TestDeleterDependent("DR_DELETER_3");

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependentResource(dr1).withActivationCondition(notMetCondition)
        .addDependentResource(drDeleter).dependsOn(dr1)
        .addDependentResource(drDeleter2).dependsOn(drDeleter)
        .withActivationCondition(notMetCondition)
        .addDependentResource(drDeleter3).dependsOn(drDeleter2)
        .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    Assertions.assertThat(res.getReconciledDependents()).isEmpty();
    assertThat(executionHistory).deleted(drDeleter, drDeleter3)
        .notReconciled(dr1,
            drDeleter2);
  }

  @Test
  void activationConditionOnlyCalledOnceOnDeleteDependents() {
    TestDeleterDependent drDeleter2 = new TestDeleterDependent("DR_DELETER_2");
    var condition = mock(Condition.class);
    when(condition.isMet(any(), any(), any())).thenReturn(false);

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependentResource(drDeleter).withActivationCondition(condition)
        .addDependentResource(drDeleter2).dependsOn(drDeleter)
        .build();

    workflow.reconcile(new TestCustomResource(), mockContext);

    assertThat(executionHistory).deleted(drDeleter2);
    verify(condition, times(1)).isMet(any(), any(), any());
  }

}

package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.AggregatedOperatorException;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ManagedWorkflowAndDependentResourceContext;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.ExecutionAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowReconcileExecutorTest extends AbstractWorkflowExecutorTest {
  private static final Logger log = LoggerFactory.getLogger(WorkflowReconcileExecutorTest.class);

  @SuppressWarnings("unchecked")
  Context<TestCustomResource> mockContext = spy(Context.class);

  ExecutorService executorService = Executors.newCachedThreadPool();

  TestDependent dr3 = new TestDependent("DR_3");
  TestDependent dr4 = new TestDependent("DR_4");

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup(TestInfo testInfo) {
    log.debug("==> Starting test {}", testInfo.getDisplayName());
    when(mockContext.managedWorkflowAndDependentResourceContext())
        .thenReturn(mock(ManagedWorkflowAndDependentResourceContext.class));
    when(mockContext.getWorkflowExecutorService()).thenReturn(executorService);
    when(mockContext.eventSourceRetriever()).thenReturn(mock(EventSourceRetriever.class));
  }

  @Test
  void reconcileTopLevelResources() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
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
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dr1)
            .addDependentResourceAndConfigure(dr2)
            .dependsOn(dr1)
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

    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dr1)
            .addDependentResourceAndConfigure(dr2)
            .dependsOn(dr1)
            .addDependentResourceAndConfigure(dr3)
            .dependsOn(dr1)
            .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    assertThat(executionHistory).reconciledInOrder(dr1, dr2).reconciledInOrder(dr1, dr3);
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1, dr2, dr3);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void diamondShareWorkflowReconcile() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dr1)
            .addDependentResourceAndConfigure(dr2)
            .dependsOn(dr1)
            .addDependentResourceAndConfigure(dr3)
            .dependsOn(dr1)
            .addDependentResourceAndConfigure(dr4)
            .dependsOn(dr3)
            .dependsOn(dr2)
            .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    assertThat(executionHistory).reconciledInOrder(dr1, dr2, dr4).reconciledInOrder(dr1, dr3, dr4);

    Assertions.assertThat(res.getReconciledDependents())
        .containsExactlyInAnyOrder(dr1, dr2, dr3, dr4);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void exceptionHandlingSimpleCases() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(drError)
            .withThrowExceptionFurther(false)
            .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    assertThrows(AggregatedOperatorException.class, res::throwAggregateExceptionIfErrorsPresent);

    assertThat(executionHistory).reconciled(drError);
    Assertions.assertThat(res.getErroredDependents()).containsOnlyKeys(drError);
    Assertions.assertThat(res.getReconciledDependents()).isEmpty();
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void dependentsOnErroredResourceNotReconciled() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dr1)
            .addDependentResourceAndConfigure(drError)
            .dependsOn(dr1)
            .addDependentResourceAndConfigure(dr2)
            .dependsOn(drError)
            .withThrowExceptionFurther(false)
            .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);
    assertThrows(AggregatedOperatorException.class, res::throwAggregateExceptionIfErrorsPresent);

    assertThat(executionHistory).reconciled(dr1, drError).notReconciled(dr2);
    Assertions.assertThat(res.getErroredDependents()).containsOnlyKeys(drError);
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1);
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void oneBranchErrorsOtherCompletes() {

    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dr1)
            .addDependentResourceAndConfigure(drError)
            .dependsOn(dr1)
            .addDependentResourceAndConfigure(dr2)
            .dependsOn(dr1)
            .addDependentResourceAndConfigure(dr3)
            .dependsOn(dr2)
            .withThrowExceptionFurther(false)
            .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);
    assertThrows(AggregatedOperatorException.class, res::throwAggregateExceptionIfErrorsPresent);

    assertThat(executionHistory).reconciledInOrder(dr1, dr2, dr3).reconciledInOrder(dr1, drError);
    Assertions.assertThat(res.getErroredDependents()).containsOnlyKeys(drError);
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1, dr2, dr3);
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void onlyOneDependsOnErroredResourceNotReconciled() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dr1)
            .addDependentResource(drError)
            .addDependentResourceAndConfigure(dr2)
            .dependsOn(drError, dr1)
            .withThrowExceptionFurther(false)
            .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);
    assertThrows(AggregatedOperatorException.class, res::throwAggregateExceptionIfErrorsPresent);

    assertThat(executionHistory).notReconciled(dr2);
    Assertions.assertThat(res.getErroredDependents()).containsKey(drError);
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1);
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void simpleReconcileCondition() {
    final var result = "Some error message";
    final var unmetWithResult =
        new DetailedCondition<ConfigMap, TestCustomResource, String>() {
          @Override
          public Result<String> detailedIsMet(
              DependentResource<ConfigMap, TestCustomResource> dependentResource,
              TestCustomResource primary,
              Context<TestCustomResource> context) {
            return Result.withResult(false, result);
          }
        };

    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResourceAndConfigure(dr1)
            .withReconcilePrecondition(unmetWithResult)
            .addDependentResourceAndConfigure(dr2)
            .withReconcilePrecondition(metCondition)
            .addDependentResourceAndConfigure(drDeleter)
            .withReconcilePrecondition(notMetCondition)
            .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    assertThat(executionHistory).notReconciled(dr1).reconciled(dr2).deleted(drDeleter);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr2);
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
    res.getDependentConditionResult(dr1, Condition.Type.RECONCILE, String.class)
        .ifPresentOrElse(s -> assertEquals(result, s), org.junit.jupiter.api.Assertions::fail);
  }

  @Test
  void triangleOnceConditionNotMet() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dr1)
            .addDependentResourceAndConfigure(dr2)
            .dependsOn(dr1)
            .addDependentResourceAndConfigure(drDeleter)
            .withReconcilePrecondition(notMetCondition)
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

    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dr1)
            .addDependentResourceAndConfigure(dr2)
            .dependsOn(dr1)
            .withReconcilePrecondition(notMetCondition)
            .addDependentResourceAndConfigure(drDeleter)
            .dependsOn(dr2)
            .withReconcilePrecondition(metCondition)
            .addDependentResourceAndConfigure(drDeleter2)
            .dependsOn(drDeleter)
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

    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(drError)
            .addDependentResourceAndConfigure(drDeleter)
            .withReconcilePrecondition(notMetCondition)
            .addDependentResourceAndConfigure(drDeleter2)
            .dependsOn(drError, drDeleter)
            .withReconcilePrecondition(metCondition)
            .withThrowExceptionFurther(false)
            .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);
    assertThrows(AggregatedOperatorException.class, res::throwAggregateExceptionIfErrorsPresent);

    assertThat(executionHistory).deleted(drDeleter2, drDeleter).reconciled(drError);

    Assertions.assertThat(res.getErroredDependents()).containsOnlyKeys(drError);
    Assertions.assertThat(res.getReconciledDependents()).isEmpty();
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void oneDependsOnConditionNotMet() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dr1)
            .addDependentResourceAndConfigure(dr2)
            .withReconcilePrecondition(notMetCondition)
            .addDependentResourceAndConfigure(drDeleter)
            .dependsOn(dr1, dr2)
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
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dr1)
            .addDependentResourceAndConfigure(drDeleter)
            .dependsOn(dr1)
            .withReconcilePrecondition(notMetCondition)
            .addDependentResourceAndConfigure(drDeleter2)
            .dependsOn(dr1, drDeleter)
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

    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dr1)
            .addDependentResourceAndConfigure(drDeleter)
            .withReconcilePrecondition(notMetCondition)
            .dependsOn(dr1)
            .addDependentResourceAndConfigure(drDeleter2)
            .dependsOn(drDeleter)
            .addDependentResourceAndConfigure(drDeleter3)
            .dependsOn(drDeleter)
            .addDependentResourceAndConfigure(drDeleter4)
            .dependsOn(drDeleter3)
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

    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResourceAndConfigure(drDeleter)
            .withReconcilePrecondition(notMetCondition)
            .addDependentResourceAndConfigure(drDeleter2)
            .dependsOn(drDeleter)
            .addDependentResourceAndConfigure(drDeleter3)
            .dependsOn(drDeleter)
            .withDeletePostcondition(this.notMetCondition)
            .addDependentResourceAndConfigure(drDeleter4)
            .dependsOn(drDeleter3, drDeleter2)
            .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    assertThat(executionHistory)
        .notReconciled(drDeleter)
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

    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResourceAndConfigure(drDeleter)
            .withReconcilePrecondition(notMetCondition)
            .addDependentResourceAndConfigure(drDeleter2)
            .dependsOn(drDeleter)
            .addDependentResourceAndConfigure(errorDD)
            .dependsOn(drDeleter)
            .addDependentResourceAndConfigure(drDeleter3)
            .dependsOn(errorDD, drDeleter2)
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
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResourceAndConfigure(dr1)
            .withReadyPostcondition(metCondition)
            .addDependentResourceAndConfigure(dr2)
            .dependsOn(dr1)
            .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    assertThat(executionHistory).reconciledInOrder(dr1, dr2);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1, dr2);
    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  void readyConditionNotMetTrivialCase() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResourceAndConfigure(dr1)
            .withReadyPostcondition(notMetCondition)
            .addDependentResourceAndConfigure(dr2)
            .dependsOn(dr1)
            .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    assertThat(executionHistory).reconciled(dr1).notReconciled(dr2);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1);
    Assertions.assertThat(res.getNotReadyDependents()).containsExactlyInAnyOrder(dr1);
  }

  @Test
  void readyConditionNotMetInOneParent() {

    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResourceAndConfigure(dr1)
            .withReadyPostcondition(notMetCondition)
            .addDependentResource(dr2)
            .addDependentResourceAndConfigure(dr3)
            .dependsOn(dr1, dr2)
            .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    assertThat(executionHistory).reconciled(dr1, dr2).notReconciled(dr3);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1, dr2);
    Assertions.assertThat(res.getNotReadyDependents()).containsExactlyInAnyOrder(dr1);
  }

  @Test
  void diamondShareWithReadyCondition() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dr1)
            .addDependentResourceAndConfigure(dr2)
            .dependsOn(dr1)
            .withReadyPostcondition(notMetCondition)
            .addDependentResourceAndConfigure(dr3)
            .dependsOn(dr1)
            .addDependentResourceAndConfigure(dr4)
            .dependsOn(dr2, dr3)
            .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    assertThat(executionHistory)
        .reconciledInOrder(dr1, dr2)
        .reconciledInOrder(dr1, dr3)
        .notReconciled(dr4);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).containsExactlyInAnyOrder(dr1, dr2, dr3);
    Assertions.assertThat(res.getNotReadyDependents()).containsExactlyInAnyOrder(dr2);
  }

  @Test
  void garbageCollectedResourceIsDeletedIfReconcilePreconditionDoesNotHold() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResourceAndConfigure(gcDeleter)
            .withReconcilePrecondition(notMetCondition)
            .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    assertThat(executionHistory).deleted(gcDeleter);
  }

  @Test
  void garbageCollectedDeepResourceIsDeletedIfReconcilePreconditionDoesNotHold() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResourceAndConfigure(dr1)
            .withReconcilePrecondition(notMetCondition)
            .addDependentResourceAndConfigure(gcDeleter)
            .dependsOn(dr1)
            .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    assertThat(executionHistory).deleted(gcDeleter);
  }

  @Test
  void notReconciledIfActivationConditionNotMet() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResourceAndConfigure(dr1)
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
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResourceAndConfigure(dr1)
            .withActivationCondition(notMetCondition)
            .addDependentResource(dr2)
            .addDependentResourceAndConfigure(dr3)
            .dependsOn(dr1)
            .build();
    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    assertThat(executionHistory).reconciled(dr2).notReconciled(dr1, dr3);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getReconciledDependents()).contains(dr2);
  }

  @Test
  void readyConditionNotCheckedOnNonActiveDependent() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResourceAndConfigure(dr1)
            .withActivationCondition(notMetCondition)
            .withReadyPostcondition(notMetCondition)
            .addDependentResource(dr2)
            .addDependentResourceAndConfigure(dr3)
            .dependsOn(dr1)
            .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    Assertions.assertThat(res.getNotReadyDependents()).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void reconcilePreconditionNotCheckedOnNonActiveDependent() {
    var precondition = mock(Condition.class);

    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResourceAndConfigure(dr1)
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

    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResourceAndConfigure(dr1)
            .withActivationCondition(notMetCondition)
            .addDependentResourceAndConfigure(drDeleter)
            .dependsOn(dr1)
            .addDependentResourceAndConfigure(drDeleter2)
            .dependsOn(drDeleter)
            .withActivationCondition(notMetCondition)
            .addDependentResourceAndConfigure(drDeleter3)
            .dependsOn(drDeleter2)
            .build();

    var res = workflow.reconcile(new TestCustomResource(), mockContext);

    Assertions.assertThat(res.getReconciledDependents()).isEmpty();
    assertThat(executionHistory).deleted(drDeleter, drDeleter3).notReconciled(dr1, drDeleter2);
  }

  @Test
  @SuppressWarnings("unchecked")
  void activationConditionOnlyCalledOnceOnDeleteDependents() {
    TestDeleterDependent drDeleter2 = new TestDeleterDependent("DR_DELETER_2");
    var condition = mock(Condition.class);
    when(condition.isMet(any(), any(), any())).thenReturn(false);

    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResourceAndConfigure(drDeleter)
            .withActivationCondition(condition)
            .addDependentResourceAndConfigure(drDeleter2)
            .dependsOn(drDeleter)
            .build();

    workflow.reconcile(new TestCustomResource(), mockContext);

    assertThat(executionHistory).deleted(drDeleter2);
    verify(condition, times(1)).isMet(any(), any(), any());
  }

  @Test
  void resultFromReadyConditionShouldBeAvailableIfExisting() {
    final var result = Integer.valueOf(42);
    final var resultCondition =
        new DetailedCondition<>() {
          @Override
          public Result<Object> detailedIsMet(
              DependentResource<Object, HasMetadata> dependentResource,
              HasMetadata primary,
              Context<HasMetadata> context) {
            return new Result<>() {
              @Override
              public Object getDetail() {
                return result;
              }

              @Override
              public boolean isSuccess() {
                return false; // force not ready
              }
            };
          }
        };
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResourceAndConfigure(dr1)
            .withReadyPostcondition(resultCondition)
            .build();

    final var reconcileResult = workflow.reconcile(new TestCustomResource(), mockContext);
    assertEquals(
        result, reconcileResult.getNotReadyDependentResult(dr1, Integer.class).orElseThrow());
  }

  @Test
  void shouldThrowIllegalArgumentExceptionIfTypesDoNotMatch() {
    final var result = "FOO";
    final var resultCondition =
        new DetailedCondition<>() {
          @Override
          public Result<Object> detailedIsMet(
              DependentResource<Object, HasMetadata> dependentResource,
              HasMetadata primary,
              Context<HasMetadata> context) {
            return new Result<>() {
              @Override
              public Object getDetail() {
                return result;
              }

              @Override
              public boolean isSuccess() {
                return false; // force not ready
              }
            };
          }
        };
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResourceAndConfigure(dr1)
            .withReadyPostcondition(resultCondition)
            .build();

    final var reconcileResult = workflow.reconcile(new TestCustomResource(), mockContext);
    final var expectedResultType = Integer.class;
    final var e =
        assertThrows(
            IllegalArgumentException.class,
            () -> reconcileResult.getNotReadyDependentResult(dr1, expectedResultType));
    final var message = e.getMessage();
    assertTrue(message.contains(dr1.name()));
    assertTrue(message.contains(expectedResultType.getSimpleName()));
    assertTrue(message.contains(result));
  }

  @Test
  void shouldReturnEmptyIfNoConditionResultExists() {
    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResourceAndConfigure(dr1)
            .withReadyPostcondition(notMetCondition)
            .build();

    final var reconcileResult = workflow.reconcile(new TestCustomResource(), mockContext);
    assertTrue(reconcileResult.getNotReadyDependentResult(dr1, Integer.class).isEmpty());
  }
}

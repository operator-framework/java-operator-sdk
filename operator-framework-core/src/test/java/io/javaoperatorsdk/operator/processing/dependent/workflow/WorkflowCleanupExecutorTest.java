package io.javaoperatorsdk.operator.processing.dependent.workflow;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.AggregatedOperatorException;
import io.javaoperatorsdk.operator.processing.dependent.workflow.builder.WorkflowBuilder;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.ExecutionAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowCleanupExecutorTest extends AbstractWorkflowExecutorTest {

  protected TestDeleterDependent dd1 = new TestDeleterDependent("DR_DELETER_1");
  protected TestDeleterDependent dd2 = new TestDeleterDependent("DR_DELETER_2");
  protected TestDeleterDependent dd3 = new TestDeleterDependent("DR_DELETER_3");

  @Test
  void cleanUpDiamondWorkflow() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dd1).build()
        .addDependent(dr1).dependsOn(dd1).build()
        .addDependent(dd2).dependsOn(dd1).build()
        .addDependent(dd3).dependsOn(dr1, dd2).build()
        .build();

    var res = workflow.cleanup(new TestCustomResource(), null);

    assertThat(executionHistory).reconciledInOrder(dd3, dd2, dd1).notReconciled(dr1);

    Assertions.assertThat(res.getDeleteCalledOnDependents()).containsExactlyInAnyOrder(dd1, dd2,
        dd3);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getPostConditionNotMetDependents()).isEmpty();
  }

  @Test
  void dontDeleteIfDependentErrored() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dd1).build()
        .addDependent(dd2).dependsOn(dd1).build()
        .addDependent(dd3).dependsOn(dd2).build()
        .addDependent(errorDD).dependsOn(dd2).build()
        .withThrowExceptionFurther(false)
        .build();

    var res = workflow.cleanup(new TestCustomResource(), null);
    assertThrows(AggregatedOperatorException.class,
        res::throwAggregateExceptionIfErrorsPresent);

    assertThat(executionHistory).deleted(dd3, errorDD).notReconciled(dd1, dd2);

    Assertions.assertThat(res.getDeleteCalledOnDependents()).containsExactlyInAnyOrder(dd3);
    Assertions.assertThat(res.getErroredDependents()).containsOnlyKeys(errorDD);
    Assertions.assertThat(res.getPostConditionNotMetDependents()).isEmpty();
  }


  @Test
  void cleanupConditionTrivialCase() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dd1).build()
        .addDependent(dd2).dependsOn(dd1).withDeletePostCondition(noMetDeletePostCondition).build()
        .build();

    var res = workflow.cleanup(new TestCustomResource(), null);

    assertThat(executionHistory).deleted(dd2).notReconciled(dd1);
    Assertions.assertThat(res.getDeleteCalledOnDependents()).containsExactlyInAnyOrder(dd2);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getPostConditionNotMetDependents()).containsExactlyInAnyOrder(dd2);
  }

  @Test
  void cleanupConditionMet() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dd1).build()
        .addDependent(dd2).dependsOn(dd1).withDeletePostCondition(metDeletePostCondition).build()
        .build();

    var res = workflow.cleanup(new TestCustomResource(), null);

    assertThat(executionHistory).deleted(dd2, dd1);

    Assertions.assertThat(res.getDeleteCalledOnDependents()).containsExactlyInAnyOrder(dd1, dd2);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getPostConditionNotMetDependents()).isEmpty();
  }

  @Test
  void cleanupConditionDiamondWorkflow() {
    TestDeleterDependent dd4 = new TestDeleterDependent("DR_DELETER_4");

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dd1).build()
        .addDependent(dd2).dependsOn(dd1).build()
        .addDependent(dd3).dependsOn(dd1).withDeletePostCondition(noMetDeletePostCondition).build()
        .addDependent(dd4).dependsOn(dd2, dd3).build()
        .build();

    var res = workflow.cleanup(new TestCustomResource(), null);

    assertThat(executionHistory)
        .reconciledInOrder(dd4, dd2)
        .reconciledInOrder(dd4, dd3)
        .notReconciled(dr1);

    Assertions.assertThat(res.getDeleteCalledOnDependents()).containsExactlyInAnyOrder(dd4, dd3,
        dd2);
    Assertions.assertThat(res.getErroredDependents()).isEmpty();
    Assertions.assertThat(res.getPostConditionNotMetDependents()).containsExactlyInAnyOrder(dd3);
  }

  @Test
  void dontDeleteIfGarbageCollected() {
    GarbageCollectedDeleter gcDel = new GarbageCollectedDeleter("GC_DELETER");
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(gcDel).build()
        .build();

    var res = workflow.cleanup(new TestCustomResource(), null);

    assertThat(executionHistory)
        .notReconciled(gcDel);

    Assertions.assertThat(res.getDeleteCalledOnDependents()).isEmpty();
  }

}

package io.javaoperatorsdk.operator.processing.dependent.workflow;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.processing.dependent.workflow.builder.WorkflowBuilder;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.ExecutionAssert.assertThat;

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

    workflow.cleanup(new TestCustomResource(), null);

    assertThat(executionHistory).reconciledInOrder(dd3, dd2, dd1).notReconciled(dr1);
  }

  @Test
  void dontDeleteIfDependentErrored() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dd1).build()
        .addDependent(dd2).dependsOn(dd1).build()
        .addDependent(dd3).dependsOn(dd2).build()
        .addDependent(errorDD).dependsOn(dd2).build()
        .build();

    workflow.cleanup(new TestCustomResource(), null);

    assertThat(executionHistory).deleted(dd3, errorDD).notReconciled(dd1, dd2);
  }


  @Test
  void cleanupConditionTrivialCase() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dd1).build()
        .addDependent(dd2).dependsOn(dd1).withDeletePostCondition(noMetDeletePostCondition).build()
        .build();

    workflow.cleanup(new TestCustomResource(), null);

    assertThat(executionHistory).deleted(dd2).notReconciled(dd1);
  }

  @Test
  void cleanupConditionMet() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dd1).build()
        .addDependent(dd2).dependsOn(dd1).withDeletePostCondition(metDeletePostCondition).build()
        .build();

    workflow.cleanup(new TestCustomResource(), null);

    assertThat(executionHistory).deleted(dd2, dd1);
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

    workflow.cleanup(new TestCustomResource(), null);

    assertThat(executionHistory)
        .reconciledInOrder(dd4, dd2)
        .reconciledInOrder(dd4, dd3)
        .notReconciled(dr1);
  }

  @Test
  void dontDeleteIfGarbageCollected() {
    GarbageCollectedDeleter gcDel = new GarbageCollectedDeleter("GC_DELETER");
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(gcDel).build()
        .build();

    workflow.cleanup(new TestCustomResource(), null);

    assertThat(executionHistory)
        .notReconciled(gcDel);
  }

}

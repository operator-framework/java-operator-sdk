package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.javaoperatorsdk.operator.processing.dependent.workflow.builder.WorkflowBuilder;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.ExecutionAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowCleanupExecutorTest extends AbstractWorkflowExecutorTest {

  protected TestDeleterDependent dd1 = new TestDeleterDependent("DR_DELETER_1");
  protected TestDeleterDependent dd2 = new TestDeleterDependent("DR_DELETER_2");
  protected TestDeleterDependent dd3 = new TestDeleterDependent("DR_DELETER_3");

  // @Test
  void cleanUpDiamondWorkflow() {
    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(dd1).build()
        .addDependent(dr1).dependsOn(dd1).build()
        .addDependent(dd2).dependsOn(dd1).build()
        .addDependent(dd3).dependsOn(dr1, dd2).build()
        .build();

    workflow.cleanup(new TestCustomResource(), null);

    assertThat(executionHistory).reconciledInOrder(dd1, dd2, dd3).reconciledInOrder();
  }



}

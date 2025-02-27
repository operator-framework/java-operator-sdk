package io.javaoperatorsdk.operator.processing.dependent.workflow;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WorkflowBuilderTest {

  @Test
  void workflowIsCleanerIfAtLeastOneDRIsCleaner() {
    var dr = mock(DependentResource.class);
    when(dr.name()).thenReturn("dr");
    var deleter = mock(DependentResource.class);
    when(deleter.isDeletable()).thenReturn(true);
    when(deleter.name()).thenReturn("deleter");

    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(deleter)
            .addDependentResource(dr)
            .build();

    assertThat(workflow.hasCleaner()).isTrue();
  }
}

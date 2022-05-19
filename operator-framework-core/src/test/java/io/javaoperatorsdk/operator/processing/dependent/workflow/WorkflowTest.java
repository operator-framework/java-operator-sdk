package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.builder.WorkflowBuilder;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SuppressWarnings("rawtypes")
class WorkflowTest {

  @Test
  void calculatesTopLevelResources() {
    var dr1 = mock(DependentResource.class);
    var dr2 = mock(DependentResource.class);
    var independentDR = mock(DependentResource.class);

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(independentDR).build()
        .addDependent(dr1).build()
        .addDependent(dr2).dependsOn(dr1).build()
        .build();

    Set<DependentResource> topResources =
        workflow.getTopLevelDependentResources().stream()
            .map(DependentResourceNode::getDependentResource)
            .collect(Collectors.toSet());

    assertThat(topResources).containsExactlyInAnyOrder(dr1, independentDR);
  }

  @Test
  void calculatesBottomLevelResources() {
    var dr1 = mock(DependentResource.class);
    var dr2 = mock(DependentResource.class);
    var independentDR = mock(DependentResource.class);

    Workflow<TestCustomResource> workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependent(independentDR).build()
        .addDependent(dr1).build()
        .addDependent(dr2).dependsOn(dr1).build()
        .build();

    Set<DependentResource> bottomResources =
        workflow.getBottomLevelResource().stream()
            .map(DependentResourceNode::getDependentResource)
            .collect(Collectors.toSet());

    assertThat(bottomResources).containsExactlyInAnyOrder(dr2, independentDR);
  }

}

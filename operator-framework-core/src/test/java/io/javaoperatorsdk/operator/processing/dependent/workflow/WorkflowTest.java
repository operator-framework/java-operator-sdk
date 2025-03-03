package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@SuppressWarnings("rawtypes")
class WorkflowTest {

  @Test
  void zeroTopLevelDRShouldThrowException() {
    var dr1 = mockDependent("dr1");
    var dr2 = mockDependent("dr2");
    var dr3 = mockDependent("dr3");

    var cyclicWorkflowBuilderSetup =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResourceAndConfigure(dr1)
            .dependsOn()
            .addDependentResourceAndConfigure(dr2)
            .dependsOn(dr1)
            .addDependentResourceAndConfigure(dr3)
            .dependsOn(dr2)
            .addDependentResourceAndConfigure(dr1)
            .dependsOn(dr2);

    assertThrows(IllegalStateException.class, cyclicWorkflowBuilderSetup::build);
  }

  @Test
  void calculatesTopLevelResources() {
    var dr1 = mockDependent("dr1");
    var dr2 = mockDependent("dr2");
    var independentDR = mockDependent("independentDR");

    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(independentDR)
            .addDependentResource(dr1)
            .addDependentResourceAndConfigure(dr2)
            .dependsOn(dr1)
            .buildAsDefaultWorkflow();

    Set<DependentResource> topResources =
        workflow.getTopLevelDependentResources().stream()
            .map(DependentResourceNode::getDependentResource)
            .collect(Collectors.toSet());

    assertThat(topResources).containsExactlyInAnyOrder(dr1, independentDR);
  }

  @Test
  void calculatesBottomLevelResources() {
    var dr1 = mockDependent("dr1");
    var dr2 = mockDependent("dr2");
    var independentDR = mockDependent("independentDR");

    final var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(independentDR)
            .addDependentResource(dr1)
            .addDependentResourceAndConfigure(dr2)
            .dependsOn(dr1)
            .buildAsDefaultWorkflow();

    Set<DependentResource> bottomResources =
        workflow.getBottomLevelDependentResources().stream()
            .map(DependentResourceNode::getDependentResource)
            .collect(Collectors.toSet());

    assertThat(bottomResources).containsExactlyInAnyOrder(dr2, independentDR);
  }

  @Test
  void isDeletableShouldWork() {
    var dr = mock(DependentResource.class);
    assertFalse(DefaultWorkflow.isDeletable(dr.getClass()));

    dr = mock(DependentResource.class, withSettings().extraInterfaces(Deleter.class));
    assertTrue(DefaultWorkflow.isDeletable(dr.getClass()));

    dr = mock(KubernetesDependentResource.class);
    assertFalse(DefaultWorkflow.isDeletable(dr.getClass()));

    dr = mock(KubernetesDependentResource.class, withSettings().extraInterfaces(Deleter.class));
    assertTrue(DefaultWorkflow.isDeletable(dr.getClass()));

    dr =
        mock(
            KubernetesDependentResource.class,
            withSettings().extraInterfaces(Deleter.class, GarbageCollected.class));
    assertFalse(DefaultWorkflow.isDeletable(dr.getClass()));
  }

  static DependentResource mockDependent(String name) {
    var res = mock(DependentResource.class);
    when(res.name()).thenReturn(name);
    return res;
  }
}

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

@SuppressWarnings("rawtypes")
class WorkflowTest {

  @Test
  void calculatesTopLevelResources() {
    var dr1 = mock(DependentResource.class);
    var dr2 = mock(DependentResource.class);
    var independentDR = mock(DependentResource.class);

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependentResource(independentDR)
        .addDependentResource(dr1)
        .addDependentResource(dr2).dependsOn(dr1)
        .build();

    Set<DependentResource> topResources =
        workflow.getTopLevelDependentResources().stream()
            .map(workflow::getDependentResourceFor)
            .collect(Collectors.toSet());

    assertThat(topResources).containsExactlyInAnyOrder(dr1, independentDR);
  }

  @Test
  void calculatesBottomLevelResources() {
    var dr1 = mock(DependentResource.class);
    var dr2 = mock(DependentResource.class);
    var independentDR = mock(DependentResource.class);

    Workflow<TestCustomResource> workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependentResource(independentDR)
        .addDependentResource(dr1)
        .addDependentResource(dr2).dependsOn(dr1)
        .build();

    Set<DependentResource> bottomResources =
        workflow.getBottomLevelResource().stream()
            .map(workflow::getDependentResourceFor)
            .collect(Collectors.toSet());

    assertThat(bottomResources).containsExactlyInAnyOrder(dr2, independentDR);
  }


  @Test
  void isDeletableShouldWork() {
    var dr = mock(DependentResource.class);
    assertFalse(Workflow.isDeletable(dr.getClass()));

    dr = mock(DependentResource.class, withSettings().extraInterfaces(Deleter.class));
    assertTrue(Workflow.isDeletable(dr.getClass()));

    dr = mock(KubernetesDependentResource.class);
    assertFalse(Workflow.isDeletable(dr.getClass()));

    dr = mock(KubernetesDependentResource.class, withSettings().extraInterfaces(Deleter.class));
    assertTrue(Workflow.isDeletable(dr.getClass()));

    dr = mock(KubernetesDependentResource.class, withSettings().extraInterfaces(Deleter.class,
        GarbageCollected.class));
    assertFalse(Workflow.isDeletable(dr.getClass()));
  }
}

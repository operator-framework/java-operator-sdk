package io.javaoperatorsdk.operator.processing.dependent.workflow;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WorkflowBuilderTest {

  @Test
  void workflowIsCleanerIfAtLeastOneDRIsCleaner() {
    var dr = mock(DependentResource.class);
    when(dr.name()).thenReturn("dr");

    var workflow = new WorkflowBuilder<TestCustomResource>()
        .addDependentResource(new DeleterDependentResource())
        .addDependentResource(dr)
        .build();

    assertThat(workflow.hasCleaner()).isTrue();
  }

  static class DeleterDependentResource
      extends KubernetesDependentResource<ConfigMap, TestCustomResource>
      implements Deleter<TestCustomResource> {
    public DeleterDependentResource() {
      super(ConfigMap.class);
    }
  }

}

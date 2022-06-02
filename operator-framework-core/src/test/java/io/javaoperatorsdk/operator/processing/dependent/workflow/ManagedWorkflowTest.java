package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflowTestUtils.createDRS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class ManagedWorkflowTest {

  ManagedWorkflowSupport managedWorkflowSupportMock = mock(ManagedWorkflowSupport.class);
  KubernetesClient kubernetesClientMock = mock(KubernetesClient.class);

  @Test
  void checksIfWorkflowEmpty() {
    var mockWorkflow = mock(Workflow.class);
    when(managedWorkflowSupportMock.toWorkflow(any(), any(), any())).thenReturn(mockWorkflow);
    when(managedWorkflowSupportMock.createAndConfigureFrom(any(), any()))
        .thenReturn(mock(DependentResource.class));
    assertThat(managedWorkflow().isEmptyWorkflow()).isTrue();

    when(mockWorkflow.getDependentResources()).thenReturn(Set.of(mock(DependentResource.class)));
    assertThat(managedWorkflow(createDRS("name1")).isEmptyWorkflow()).isFalse();
  }

  @Test
  void isCleanerIfAtLeastOneDRIsDeleterAndNoGC() {
    var mockWorkflow = mock(Workflow.class);
    when(managedWorkflowSupportMock.toWorkflow(any(), any(), any())).thenReturn(mockWorkflow);
    when(managedWorkflowSupportMock.createAndConfigureFrom(any(), any()))
        .thenReturn(mock(DependentResource.class));
    when(mockWorkflow.getDependentResources()).thenReturn(Set.of(mock(DependentResource.class)));

    assertThat(managedWorkflow(createDRS("name1")).isCleaner()).isFalse();

    when(mockWorkflow.getDependentResources()).thenReturn(
        Set.of(mock(DependentResource.class, withSettings().extraInterfaces(Deleter.class))));
    assertThat(managedWorkflow(createDRS("name1")).isCleaner()).isTrue();

    when(mockWorkflow.getDependentResources()).thenReturn(Set.of(mock(DependentResource.class,
        withSettings().extraInterfaces(Deleter.class, GarbageCollected.class))));
    assertThat(managedWorkflow(createDRS("name1")).isCleaner()).isFalse();
  }

  ManagedWorkflow managedWorkflow(DependentResourceSpec... specs) {
    return new ManagedWorkflow(kubernetesClientMock, List.of(specs), managedWorkflowSupportMock);
  }

}

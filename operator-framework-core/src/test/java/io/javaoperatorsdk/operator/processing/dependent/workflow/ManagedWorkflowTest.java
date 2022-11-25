package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflowTestUtils.createDRS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@SuppressWarnings({"rawtypes", "unchecked"})
class ManagedWorkflowTest {

  public static final String NAME = "name";

  ManagedWorkflowSupport managedWorkflowSupportMock = mock(ManagedWorkflowSupport.class);
  KubernetesClient kubernetesClientMock = mock(KubernetesClient.class);

  @Test
  void checksIfWorkflowEmpty() {
    var mockWorkflow = mock(Workflow.class);
    when(managedWorkflowSupportMock.createWorkflow(any())).thenReturn(mockWorkflow);
    when(managedWorkflowSupportMock.createAndConfigureFrom(any(), any()))
        .thenReturn(mock(DependentResource.class));
    assertThat(managedWorkflow().isEmptyWorkflow()).isTrue();

    // when(mockWorkflow.getDependentResources()).thenReturn(Set.of(mock(DependentResource.class)));
    assertThat(managedWorkflow(createDRS(NAME)).isEmptyWorkflow()).isFalse();
  }

  @Test
  void isNotCleanerIfNoDeleter() {
    var mockWorkflow = mock(Workflow.class);
    when(managedWorkflowSupportMock.createWorkflow(any())).thenReturn(mockWorkflow);
    when(managedWorkflowSupportMock.createAndConfigureFrom(any(), any()))
        .thenReturn(mock(DependentResource.class));

    assertThat(managedWorkflow(createDRS(NAME)).isCleaner()).isFalse();
  }

  @Test
  void isNotCleanerIfNoGarbageCollected() {
    var mockWorkflow = mock(Workflow.class);
    when(managedWorkflowSupportMock.createWorkflow(any())).thenReturn(mockWorkflow);
    when(managedWorkflowSupportMock.createAndConfigureFrom(any(), any()))
        .thenReturn(
            mock(DependentResource.class, withSettings().extraInterfaces(GarbageCollected.class)));

    assertThat(managedWorkflow(createDRS(NAME)).isCleaner()).isFalse();
  }

  @Test
  void isCleanerIfHasDeleter() {
    var mockWorkflow = mock(Workflow.class);
    when(managedWorkflowSupportMock.createWorkflow(any())).thenReturn(mockWorkflow);

    var spec = createDRS(NAME);
    when(managedWorkflowSupportMock.createAndConfigureFrom(eq(spec), any()))
        .thenReturn(mock(DependentResource.class, withSettings().extraInterfaces(Deleter.class)));
    assertThat(managedWorkflow(spec).isCleaner()).isTrue();
  }

  @Test
  void isNotCleanerIfDeleterIsGarbageCollected() {
    var mockWorkflow = mock(Workflow.class);
    when(managedWorkflowSupportMock.createWorkflow(any())).thenReturn(mockWorkflow);

    var spec = createDRS(NAME);
    when(managedWorkflowSupportMock.createAndConfigureFrom(eq(spec), any()))
        .thenReturn(mock(DependentResource.class,
            withSettings().extraInterfaces(Deleter.class, GarbageCollected.class)));
    assertThat(managedWorkflow(createDRS(NAME)).isCleaner()).isFalse();
  }

  ManagedWorkflow managedWorkflow(DependentResourceSpec... specs) {
    final var configuration = mock(ControllerConfiguration.class);
    final var specList = List.of(specs);
    when(configuration.getDependentResources()).thenReturn(specList);
    return ConfigurationServiceProvider.instance().getWorkflowFactory()
        .workflowFor(configuration, managedWorkflowSupportMock)
        .resolve(kubernetesClientMock, specList);
  }

}

package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflowTestUtils.createDRS;
import static io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflowTestUtils.createDRSWithTraits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class ManagedWorkflowTest {

  public static final String NAME = "name";

  @Test
  void checksIfWorkflowEmpty() {
    assertThat(managedWorkflow().isEmptyWorkflow()).isTrue();
    assertThat(managedWorkflow(createDRS(NAME)).isEmptyWorkflow()).isFalse();
  }

  @Test
  void isNotCleanerIfNoDeleter() {
    assertThat(managedWorkflow(createDRS(NAME)).isCleaner()).isFalse();
  }

  @Test
  void isNotCleanerIfNoGarbageCollected() {
    assertThat(managedWorkflow(createDRSWithTraits(NAME, GarbageCollected.class))
        .isCleaner()).isFalse();
  }

  @Test
  void isCleanerIfHasDeleter() {
    var spec = createDRSWithTraits(NAME, Deleter.class);
    assertThat(managedWorkflow(spec).isCleaner()).isTrue();
  }

  @Test
  void isNotCleanerIfDeleterIsGarbageCollected() {
    var spec = createDRSWithTraits(NAME, Deleter.class, GarbageCollected.class);
    assertThat(managedWorkflow(spec).isCleaner()).isFalse();
  }

  ManagedWorkflow managedWorkflow(DependentResourceSpec... specs) {
    final var configuration = mock(ControllerConfiguration.class);
    final var specList = List.of(specs);

    KubernetesClient kubernetesClientMock = mock(KubernetesClient.class);

    when(configuration.getDependentResources()).thenReturn(specList);
    return ConfigurationServiceProvider.instance().getWorkflowFactory()
        .workflowFor(configuration)
        .resolve(kubernetesClientMock, specList);
  }

}

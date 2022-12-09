package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;

import org.junit.jupiter.api.Test;

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

@SuppressWarnings({"rawtypes"})
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
  void isNotCleanerIfGarbageCollected() {
    assertThat(managedWorkflow(createDRSWithTraits(NAME, GarbageCollected.class))
        .isCleaner()).isFalse();
  }

  @Test
  void isCleanerShouldWork() {
    assertThat(managedWorkflow(
        createDRSWithTraits(NAME, GarbageCollected.class),
        createDRSWithTraits("foo", Deleter.class))
        .isCleaner()).isTrue();

    assertThat(managedWorkflow(
        createDRSWithTraits("foo", Deleter.class),
        createDRSWithTraits(NAME, GarbageCollected.class))
        .isCleaner()).isTrue();
  }

  @Test
  void isCleanerIfHasDeleter() {
    var spec = createDRSWithTraits(NAME, Deleter.class);
    assertThat(managedWorkflow(spec).isCleaner()).isTrue();
  }

  ManagedWorkflow managedWorkflow(DependentResourceSpec... specs) {
    final var configuration = mock(ControllerConfiguration.class);
    final var specList = List.of(specs);

    when(configuration.getDependentResources()).thenReturn(specList);
    return ConfigurationServiceProvider.instance().getWorkflowFactory()
        .workflowFor(configuration);
  }

}

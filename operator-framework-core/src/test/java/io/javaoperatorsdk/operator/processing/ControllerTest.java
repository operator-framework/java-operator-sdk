package io.javaoperatorsdk.operator.processing;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.MockControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class ControllerTest {

  final Reconciler reconciler = mock(Reconciler.class);

  @Test
  void crdShouldNotBeCheckedForNativeResources() {
    final var client = MockKubernetesClient.client(Secret.class);
    final var configuration = MockControllerConfiguration.forResource(Secret.class);

    final var controller = new Controller<Secret>(reconciler, configuration, client);
    controller.start();
    verify(client, never()).apiextensions();
  }

  @Test
  void crdShouldNotBeCheckedForCustomResourcesIfDisabled() {
    final var client = MockKubernetesClient.client(TestCustomResource.class);
    final var configuration = MockControllerConfiguration.forResource(TestCustomResource.class);

    try {
      ConfigurationServiceProvider.overrideCurrent(o -> o.checkingCRDAndValidateLocalModel(false));
      final var controller = new Controller<TestCustomResource>(reconciler, configuration, client);
      controller.start();
      verify(client, never()).apiextensions();
    } finally {
      ConfigurationServiceProvider.reset();
    }
  }


  @Test
  void usesFinalizerIfThereIfReconcilerImplementsCleaner() {
    Reconciler reconciler = mock(Reconciler.class, withSettings().extraInterfaces(Cleaner.class));
    final var configuration = MockControllerConfiguration.forResource(Secret.class);

    final var controller = new Controller<Secret>(reconciler, configuration,
        MockKubernetesClient.client(Secret.class));

    assertThat(controller.useFinalizer()).isTrue();
  }
}

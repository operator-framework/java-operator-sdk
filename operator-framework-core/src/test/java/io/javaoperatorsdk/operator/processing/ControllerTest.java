package io.javaoperatorsdk.operator.processing;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class ControllerTest {

  final ControllerConfiguration configuration = mock(ControllerConfiguration.class);
  final Reconciler reconciler = mock(Reconciler.class);

  @Test
  void crdShouldNotBeCheckedForNativeResources() {
    final var client = MockKubernetesClient.client(Secret.class);

    when(configuration.getResourceClass()).thenReturn(Secret.class);

    final var controller = new Controller<Secret>(reconciler, configuration, client);
    controller.start();
    verify(client, never()).apiextensions();
  }

  @Test
  void crdShouldNotBeCheckedForCustomResourcesIfDisabled() {
    final var client = MockKubernetesClient.client(TestCustomResource.class);
    when(configuration.getResourceClass()).thenReturn(TestCustomResource.class);

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
  void crdShouldBeCheckedForCustomResourcesByDefault() {
    ConfigurationServiceProvider.reset();
    final var client = MockKubernetesClient.client(TestCustomResource.class);
    when(configuration.getResourceClass()).thenReturn(TestCustomResource.class);

    final var controller = new Controller<TestCustomResource>(reconciler, configuration, client);
    // since we're not really connected to a cluster and the CRD wouldn't be deployed anyway, we
    // expect a MissingCRDException to be thrown
    assertThrows(MissingCRDException.class, controller::start);
    verify(client, times(1)).apiextensions();
  }

  @Test
  void usesFinalizerIfThereIfReconcilerImplementsCleaner() {
    Reconciler reconciler = mock(Reconciler.class, withSettings().extraInterfaces(Cleaner.class));
    when(configuration.getResourceClass()).thenReturn(TestCustomResource.class);

    final var controller = new Controller<Secret>(reconciler,
        configuration, MockKubernetesClient.client(TestCustomResource.class));

    assertThat(controller.useFinalizer()).isTrue();
  }
}

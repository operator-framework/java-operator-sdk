package io.javaoperatorsdk.operator.processing;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class ControllerTest {
  @Test
  void crdShouldNotBeCheckedForNativeResources() {
    final var client = MockKubernetesClient.client(Secret.class);
    final var reconciler = mock(Reconciler.class);
    final var configuration = mock(ControllerConfiguration.class);
    when(configuration.getResourceClass()).thenReturn(Secret.class);

    final var controller = new Controller<Secret>(reconciler, configuration, client);
    controller.start();
    verify(client, never()).apiextensions();
  }

  @Test
  void crdShouldNotBeCheckedForCustomResourcesIfDisabled() {
    final var client = MockKubernetesClient.client(TestCustomResource.class);
    final var reconciler = mock(Reconciler.class);
    final var configuration = mock(ControllerConfiguration.class);
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
    final var reconciler = mock(Reconciler.class);
    final var configuration = mock(ControllerConfiguration.class);
    when(configuration.getResourceClass()).thenReturn(TestCustomResource.class);

    final var controller = new Controller<TestCustomResource>(reconciler, configuration, client);
    // since we're not really connected to a cluster and the CRD wouldn't be deployed anyway, we
    // expect a MissingCRDException to be thrown
    assertThrows(MissingCRDException.class, controller::start);
    verify(client, times(1)).apiextensions();
  }
}

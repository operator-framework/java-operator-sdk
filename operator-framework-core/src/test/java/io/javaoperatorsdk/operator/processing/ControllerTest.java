package io.javaoperatorsdk.operator.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.MockControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"unchecked", "rawtypes"})
class ControllerTest {

  final Reconciler reconciler = mock(Reconciler.class);

  ConfigurationService configurationService = new BaseConfigurationService();

  @Test
  void crdShouldNotBeCheckedForNativeResources() {
    final var client = MockKubernetesClient.client(Secret.class);
    final var configuration =
        MockControllerConfiguration.forResource(Secret.class, configurationService);
    final var controller = new Controller<Secret>(reconciler, configuration, client);
    controller.start();
    verify(client, never()).apiextensions();
  }

  @Test
  void crdShouldNotBeCheckedForCustomResourcesIfDisabled() {
    final var client = MockKubernetesClient.client(TestCustomResource.class);
    ConfigurationService configurationService =
        ConfigurationService.newOverriddenConfigurationService(new BaseConfigurationService(),
            o -> o.checkingCRDAndValidateLocalModel(false));

    final var configuration =
        MockControllerConfiguration.forResource(TestCustomResource.class, configurationService);
    final var controller = new Controller<TestCustomResource>(reconciler, configuration, client);
    controller.start();
    verify(client, never()).apiextensions();

  }

  @Test
  void usesFinalizerIfThereIfReconcilerImplementsCleaner() {
    Reconciler reconciler = mock(Reconciler.class, withSettings().extraInterfaces(Cleaner.class));
    final var configuration = MockControllerConfiguration.forResource(Secret.class);
    when(configuration.getConfigurationService()).thenReturn(new BaseConfigurationService());

    final var controller = new Controller<Secret>(reconciler, configuration,
        MockKubernetesClient.client(Secret.class));

    assertThat(controller.useFinalizer()).isTrue();
  }
}

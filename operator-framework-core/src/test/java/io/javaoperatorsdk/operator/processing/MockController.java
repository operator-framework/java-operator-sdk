package io.javaoperatorsdk.operator.processing;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.MockControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockController {
  public static <P extends HasMetadata> Controller<P> mockController(Class<P> primaryClass) {
    Reconciler<P> reconciler = mock(Reconciler.class);
    final var conf = MockControllerConfiguration.forResource(primaryClass);
    final var configurationService = new BaseConfigurationService();
    when(conf.getConfigurationService()).thenReturn(configurationService);
    return new Controller<>(reconciler, conf, MockKubernetesClient.client(primaryClass));
  }
}

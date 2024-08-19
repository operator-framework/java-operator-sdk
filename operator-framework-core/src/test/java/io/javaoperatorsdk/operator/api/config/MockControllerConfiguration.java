package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockControllerConfiguration {

  public static <R extends HasMetadata> ControllerConfiguration<R> forResource(
      Class<R> resourceType) {
    return forResource(resourceType, null);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <R extends HasMetadata> ControllerConfiguration<R> forResource(
      Class<R> resourceType, ConfigurationService configurationService) {
    final ControllerConfiguration configuration = mock(ControllerConfiguration.class);
    final InformerConfiguration informerConfiguration = mock(InformerConfiguration.class);
    when(configuration.getInformerConfig()).thenReturn(informerConfiguration);
    when(configuration.getResourceClass()).thenReturn(resourceType);
    when(informerConfiguration.getNamespaces()).thenReturn(DEFAULT_NAMESPACES_SET);
    when(informerConfiguration.getEffectiveNamespaces(any())).thenCallRealMethod();
    when(configuration.getName()).thenReturn(resourceType.getSimpleName());
    when(configuration.getConfigurationService()).thenReturn(configurationService);
    return configuration;
  }
}

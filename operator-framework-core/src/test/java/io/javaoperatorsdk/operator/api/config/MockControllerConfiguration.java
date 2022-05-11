package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.api.model.HasMetadata;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockControllerConfiguration {
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <R extends HasMetadata> ControllerConfiguration<R> forResource(
      Class<R> resourceType) {
    final ControllerConfiguration configuration = mock(ControllerConfiguration.class);
    when(configuration.getResourceClass()).thenReturn(resourceType);
    when(configuration.getNamespaces()).thenReturn(DEFAULT_NAMESPACES_SET);
    when(configuration.getEffectiveNamespaces()).thenCallRealMethod();
    return configuration;
  }
}

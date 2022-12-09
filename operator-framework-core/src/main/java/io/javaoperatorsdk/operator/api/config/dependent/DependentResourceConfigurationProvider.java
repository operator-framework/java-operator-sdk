package io.javaoperatorsdk.operator.api.config.dependent;

public interface DependentResourceConfigurationProvider {
  @SuppressWarnings("rawtypes")
  Object getConfigurationFor(DependentResourceSpec spec);
}

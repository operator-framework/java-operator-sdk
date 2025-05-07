package io.javaoperatorsdk.operator.api.config;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface UpdatableConfigurationService extends ConfigurationService {
  <R extends HasMetadata> void replace(ControllerConfiguration<R> config);

  static UpdatableConfigurationService newOverriddenConfigurationService(
      Consumer<ConfigurationServiceOverrider> overrider) {
    final var baseConfiguration = new BaseConfigurationService();
    if (overrider != null) {
      final var toOverride = new ConfigurationServiceOverrider(baseConfiguration);
      overrider.accept(toOverride);
      return (UpdatableConfigurationService) toOverride.build();
    }
    return baseConfiguration;
  }
}

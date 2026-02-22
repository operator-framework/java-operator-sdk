package io.javaoperatorsdk.operator.api.config.loader;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
import io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider;

public class ConfigLoader {

  private ConfigProvider configProvider;

  public ConfigLoader() {
    this(new DefatulConfigProvider());
  }

  public ConfigLoader(ConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  public Consumer<ConfigurationServiceOverrider> applyConfigs() {
    return null;
  }

  public <R extends HasMetadata>
      Consumer<ControllerConfigurationOverrider<R>> applyControllerConfigs(String controllerName) {
    return null;
  }
}

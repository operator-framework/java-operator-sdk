package io.javaoperatorsdk.operator.api.config.loader;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
import io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider;

public class ConfigLoader {

  Consumer<ConfigurationServiceOverrider> operatorConfigs() {
    return null;
  }

  <R extends HasMetadata> Consumer<ControllerConfigurationOverrider<R>> controllerConfigs(
      String controllerName) {
    return null;
  }
}

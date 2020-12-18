package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.ResourceController;

public interface ConfigurationService {

  <R extends CustomResource> ControllerConfiguration<R> getConfigurationFor(
      ResourceController<R> controller);

  default Config getClientConfiguration() {
    return Config.autoConfigure(null);
  }
}

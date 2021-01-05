package io.javaoperatorsdk.operator.config.runtime;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.AbstractConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;

public class DefaultConfigurationService extends AbstractConfigurationService {

  private static final ConfigurationService instance = new DefaultConfigurationService();

  public static ConfigurationService instance() {
    return instance;
  }

  @Override
  public <R extends CustomResource> ControllerConfiguration<R> getConfigurationFor(
      ResourceController<R> controller) {
    var config = super.getConfigurationFor(controller);
    if (config == null) {
      // create the the configuration on demand and register it
      config = new AnnotationConfiguration(controller);
      register(config);
    }
    return config;
  }
}

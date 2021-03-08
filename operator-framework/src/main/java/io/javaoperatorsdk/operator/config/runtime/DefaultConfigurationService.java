package io.javaoperatorsdk.operator.config.runtime;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.AbstractConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;

public class DefaultConfigurationService extends AbstractConfigurationService {

  private static final ConfigurationService instance = new DefaultConfigurationService();

  private DefaultConfigurationService() {
    super(Utils.loadFromProperties());
  }

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
    } else {
      // check that we don't have a controller name collision
      final var newControllerClassName = controller.getClass().getCanonicalName();
      if (!config.getAssociatedControllerClassName().equals(newControllerClassName)) {
        throwExceptionOnNameCollision(newControllerClassName, config);
      }
    }
    return config;
  }

  @Override
  public boolean validateCustomResources() {
    return Utils.shouldValidateCustomResources();
  }
}

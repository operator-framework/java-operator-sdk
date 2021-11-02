package io.javaoperatorsdk.operator.config.runtime;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.Reconciler;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;

public class DefaultConfigurationService extends BaseConfigurationService {

  private static final DefaultConfigurationService instance = new DefaultConfigurationService();

  private DefaultConfigurationService() {
    super(Utils.loadFromProperties());
  }

  public static DefaultConfigurationService instance() {
    return instance;
  }

  @Override
  public <R extends CustomResource<?, ?>> ControllerConfiguration<R> getConfigurationFor(
      Reconciler<R> controller) {
    return getConfigurationFor(controller, true);
  }

  <R extends CustomResource<?, ?>> ControllerConfiguration<R> getConfigurationFor(
      Reconciler<R> controller, boolean createIfNeeded) {
    var config = super.getConfigurationFor(controller);
    if (config == null) {
      if (createIfNeeded) {
        // create the configuration on demand and register it
        config = new AnnotationConfiguration<>(controller);
        register(config);
        getLogger().info(
            "Created configuration for controller {} with name {}",
            controller.getClass().getName(),
            config.getName());
      }
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
  public boolean checkCRDAndValidateLocalModel() {
    return Utils.shouldCheckCRDAndValidateLocalModel();
  }
}

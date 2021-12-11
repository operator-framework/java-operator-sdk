package io.javaoperatorsdk.operator.config.runtime;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

public class DefaultConfigurationService extends BaseConfigurationService {

  private static final DefaultConfigurationService instance = new DefaultConfigurationService();

  private DefaultConfigurationService() {
    super(Utils.loadFromProperties());
  }

  public static DefaultConfigurationService instance() {
    return instance;
  }

  @Override
  public <R extends HasMetadata> ControllerConfiguration<R> getConfigurationFor(
      Reconciler<R> reconciler) {
    return getConfigurationFor(reconciler, true);
  }

  <R extends HasMetadata> ControllerConfiguration<R> getConfigurationFor(
      Reconciler<R> reconciler, boolean createIfNeeded) {
    var config = super.getConfigurationFor(reconciler);
    if (config == null) {
      if (createIfNeeded) {
        // create the configuration on demand and register it
        config = new AnnotationConfiguration<>(reconciler);
        register(config);
        getLogger().info(
            "Created configuration for reconciler {} with name {}",
            reconciler.getClass().getName(),
            config.getName());
      }
    } else {
      // check that we don't have a reconciler name collision
      final var newControllerClassName = reconciler.getClass().getCanonicalName();
      if (!config.getAssociatedReconcilerClassName().equals(newControllerClassName)) {
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

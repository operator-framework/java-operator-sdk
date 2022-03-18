package io.javaoperatorsdk.operator.config.runtime;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

public class DefaultConfigurationService extends BaseConfigurationService {

  private static final DefaultConfigurationService instance = new DefaultConfigurationService();
  private boolean createIfNeeded = super.createIfNeeded();

  static {
    // register this as the default configuration service
    ConfigurationServiceProvider.setDefault(instance);
  }

  private DefaultConfigurationService() {
    super();
  }

  static DefaultConfigurationService instance() {
    return instance;
  }

  <R extends HasMetadata> ControllerConfiguration<R> getConfigurationFor(
      Reconciler<R> reconciler, boolean createIfNeeded) {
    final var previous = createIfNeeded();
    setCreateIfNeeded(createIfNeeded);
    try {
      return super.getConfigurationFor(reconciler);
    } finally {
      setCreateIfNeeded(previous);
    }
  }

  @Override
  protected boolean createIfNeeded() {
    return createIfNeeded;
  }

  public void setCreateIfNeeded(boolean createIfNeeded) {
    this.createIfNeeded = createIfNeeded;
  }

  @Override
  protected <R extends HasMetadata> ControllerConfiguration<R> configFor(Reconciler<R> reconciler) {
    return new AnnotationControllerConfiguration<>(reconciler);
  }
}

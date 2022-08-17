package io.javaoperatorsdk.operator.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

import com.fasterxml.jackson.databind.ObjectMapper;

public class BaseConfigurationService extends AbstractConfigurationService {

  private static final String LOGGER_NAME = "Default ConfigurationService implementation";
  private static final Logger logger = LoggerFactory.getLogger(LOGGER_NAME);

  public BaseConfigurationService(Version version) {
    super(version);
  }

  public BaseConfigurationService(Version version, Cloner cloner, ObjectMapper mapper) {
    super(version, cloner, mapper);
  }

  public BaseConfigurationService(Version version, Cloner cloner) {
    super(version, cloner);
  }

  public BaseConfigurationService() {
    this(Utils.loadFromProperties());
  }

  @Override
  protected void logMissingReconcilerWarning(String reconcilerKey, String reconcilersNameMessage) {
    logger.warn("Configuration for reconciler '{}' was not found. {}", reconcilerKey,
        reconcilersNameMessage);
  }

  public String getLoggerName() {
    return LOGGER_NAME;
  }

  protected Logger getLogger() {
    return logger;
  }

  @Override
  public <R extends HasMetadata> ControllerConfiguration<R> getConfigurationFor(
      Reconciler<R> reconciler) {
    var config = super.getConfigurationFor(reconciler);
    if (config == null) {
      if (createIfNeeded()) {
        // create the configuration on demand and register it
        config = configFor(reconciler);
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

  protected <R extends HasMetadata> ControllerConfiguration<R> configFor(Reconciler<R> reconciler) {
    return new AnnotationControllerConfiguration<>(reconciler);
  }

  protected boolean createIfNeeded() {
    return true;
  }

  @Override
  public boolean checkCRDAndValidateLocalModel() {
    return Utils.shouldCheckCRDAndValidateLocalModel();
  }
}

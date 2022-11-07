package io.javaoperatorsdk.operator.api.config;

import java.lang.annotation.Annotation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.Utils.Configurator;
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

  @SuppressWarnings({"unchecked"})
  protected <P extends HasMetadata> ControllerConfiguration<P> configFor(Reconciler<P> reconciler) {
    final var annotation = reconciler.getClass().getAnnotation(
        io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration.class);
    if (annotation == null) {
      throw new OperatorException(
          "Missing mandatory @"
              + io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration.class
                  .getSimpleName()
              +
              " annotation for reconciler:  " + reconciler);
    }

    final var resourceClass = (Class<P>) Utils.getFirstTypeArgumentFromSuperClassOrInterface(
        reconciler.getClass(), Reconciler.class);
    final var name = ReconcilerUtils.getNameFor(reconciler);
    final var associatedReconcilerClass =
        ResolvedControllerConfiguration.getAssociatedReconcilerClassName(reconciler.getClass());

    return new BaseControllerConfigurationBuilder(reconciler)
        .createConfiguration(annotation, resourceClass, name, associatedReconcilerClass);
  }

  protected boolean createIfNeeded() {
    return true;
  }

  @Override
  public boolean checkCRDAndValidateLocalModel() {
    return Utils.shouldCheckCRDAndValidateLocalModel();
  }

  @SuppressWarnings("rawtypes")
  private static class BaseControllerConfigurationBuilder extends ControllerConfigurationBuilder {

    private final Reconciler reconciler;

    private BaseControllerConfigurationBuilder(Reconciler reconciler) {
      this.reconciler = reconciler;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected <T> Configurator<T> configuratorFor(Class<T> typeOfObjectToConfigure) {
      return instance -> {
        if (instance instanceof AnnotationConfigurable
            && typeOfObjectToConfigure.isInstance(instance)) {
          AnnotationConfigurable configurable = (AnnotationConfigurable) instance;
          final Class<? extends Annotation> configurationClass =
              (Class<? extends Annotation>) Utils.getFirstTypeArgumentFromSuperClassOrInterface(
                  instance.getClass(), AnnotationConfigurable.class);
          final var configAnnotation = reconciler.getClass().getAnnotation(configurationClass);
          if (configAnnotation != null) {
            configurable.initFrom(configAnnotation);
          }
        }
      };
    }
  }
}

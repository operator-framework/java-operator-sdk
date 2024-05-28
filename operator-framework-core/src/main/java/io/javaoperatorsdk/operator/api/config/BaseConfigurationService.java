package io.javaoperatorsdk.operator.api.config;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.Utils.Configurator;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigurationResolver;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.config.workflow.WorkflowSpec;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.retry.Retry;

import static io.javaoperatorsdk.operator.api.config.ControllerConfiguration.CONTROLLER_NAME_AS_FIELD_MANAGER;

public class BaseConfigurationService extends AbstractConfigurationService {

  private static final String LOGGER_NAME = "Default ConfigurationService implementation";
  private static final Logger logger = LoggerFactory.getLogger(LOGGER_NAME);
  private static final ResourceClassResolver DEFAULT_RESOLVER = new DefaultResourceClassResolver();

  public BaseConfigurationService(Version version) {
    this(version, null);
  }

  public BaseConfigurationService(Version version, Cloner cloner) {
    this(version, cloner, null);
  }

  public BaseConfigurationService(Version version, Cloner cloner, KubernetesClient client) {
    super(version, cloner, null, client);
  }

  public BaseConfigurationService() {
    this(Utils.VERSION);
  }


  @Override
  protected void logMissingReconcilerWarning(String reconcilerKey, String reconcilersNameMessage) {
    logger.warn("Configuration for reconciler '{}' was not found. {}", reconcilerKey,
        reconcilersNameMessage);
  }

  @SuppressWarnings("unused")
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

  /**
   * Override if a different class resolution is needed
   *
   * @return the custom {@link ResourceClassResolver} implementation to use
   */
  protected ResourceClassResolver getResourceClassResolver() {
    return DEFAULT_RESOLVER;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected <P extends HasMetadata> ControllerConfiguration<P> configFor(Reconciler<P> reconciler) {
    final var annotation = reconciler.getClass().getAnnotation(
        io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration.class);

    ResolvedControllerConfiguration<P> config = controllerConfiguration(reconciler, annotation);

    final var workflowAnnotation = reconciler.getClass().getAnnotation(
        io.javaoperatorsdk.operator.api.reconciler.Workflow.class);
    if (workflowAnnotation != null) {
      final var specs = dependentResources(workflowAnnotation, config);
      WorkflowSpec workflowSpec = new WorkflowSpec() {
        @Override
        public List<DependentResourceSpec> getDependentResourceSpecs() {
          return specs;
        }

        @Override
        public boolean isExplicitInvocation() {
          return workflowAnnotation.explicitInvocation();
        }

        @Override
        public boolean handleExceptionsInReconciler() {
          return workflowAnnotation.handleExceptionsInReconciler();
        }

      };
      config.setWorkflowSpec(workflowSpec);
    }

    return config;
  }

  @SuppressWarnings({"unchecked"})
  private <P extends HasMetadata> ResolvedControllerConfiguration<P> controllerConfiguration(
      Reconciler<P> reconciler,
      io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration annotation) {
    Class<Reconciler<P>> reconcilerClass = (Class<Reconciler<P>>) reconciler.getClass();
    final var resourceClass = getResourceClassResolver().getPrimaryResourceClass(reconcilerClass);

    final var name = ReconcilerUtils.getNameFor(reconciler);
    final var generationAware = valueOrDefaultFromAnnotation(
        annotation,
        io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::generationAwareEventProcessing,
        "generationAwareEventProcessing");
    final var associatedReconcilerClass =
        ResolvedControllerConfiguration.getAssociatedReconcilerClassName(reconciler.getClass());

    final var context = Utils.contextFor(name);
    final Class<? extends Retry> retryClass =
        valueOrDefaultFromAnnotation(annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::retry,
            "retry");
    final var retry = Utils.instantiateAndConfigureIfNeeded(retryClass, Retry.class,
        context, configuratorFor(Retry.class, reconciler));


    final Class<? extends RateLimiter> rateLimiterClass = valueOrDefaultFromAnnotation(annotation,
        io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::rateLimiter,
        "rateLimiter");
    final var rateLimiter = Utils.instantiateAndConfigureIfNeeded(rateLimiterClass,
        RateLimiter.class, context, configuratorFor(RateLimiter.class, reconciler));

    final var reconciliationInterval = valueOrDefaultFromAnnotation(annotation,
        io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::maxReconciliationInterval,
        "maxReconciliationInterval");
    long interval = -1;
    TimeUnit timeUnit = null;
    if (reconciliationInterval != null && reconciliationInterval.interval() > 0) {
      interval = reconciliationInterval.interval();
      timeUnit = reconciliationInterval.timeUnit();
    }

    var fieldManager = valueOrDefaultFromAnnotation(annotation,
        io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::fieldManager,
        "fieldManager");
    final var dependentFieldManager =
        fieldManager.equals(CONTROLLER_NAME_AS_FIELD_MANAGER) ? name
            : fieldManager;

    var informerListLimitValue = valueOrDefaultFromAnnotation(annotation,
        io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::informerListLimit,
        "informerListLimit");
    final var informerListLimit =
        informerListLimitValue == Constants.NO_LONG_VALUE_SET ? null
            : informerListLimitValue;

    return new ResolvedControllerConfiguration<P>(
        resourceClass, name, generationAware,
        associatedReconcilerClass, retry, rateLimiter,
        ResolvedControllerConfiguration.getMaxReconciliationInterval(interval, timeUnit),
        Utils.instantiate(valueOrDefaultFromAnnotation(annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::onAddFilter,
            "onAddFilter"), OnAddFilter.class, context),
        Utils.instantiate(valueOrDefaultFromAnnotation(annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::onUpdateFilter,
            "onUpdateFilter"), OnUpdateFilter.class, context),
        Utils.instantiate(valueOrDefaultFromAnnotation(annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::genericFilter,
            "genericFilter"), GenericFilter.class, context),
        Set.of(valueOrDefaultFromAnnotation(annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::namespaces,
            "namespaces")),
        valueOrDefaultFromAnnotation(annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::finalizerName,
            "finalizerName"),
        valueOrDefaultFromAnnotation(annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::labelSelector,
            "labelSelector"),
        null,
        Utils.instantiate(
            valueOrDefaultFromAnnotation(annotation,
                io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::itemStore,
                "itemStore"),
            ItemStore.class, context),
        dependentFieldManager,
        this, informerListLimit);
  }

  protected boolean createIfNeeded() {
    return true;
  }

  @Override
  public boolean checkCRDAndValidateLocalModel() {
    return Utils.shouldCheckCRDAndValidateLocalModel();
  }

  @SuppressWarnings("unchecked")
  private static <T> T valueOrDefaultFromAnnotation(
      io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration controllerConfiguration,
      Function<io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration, T> mapper,
      String defaultMethodName) {
    try {
      if (controllerConfiguration == null) {
        return (T) io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration.class
            .getDeclaredMethod(defaultMethodName).getDefaultValue();
      } else {
        return mapper.apply(controllerConfiguration);
      }
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("rawtypes")
  private static String getName(String name, Class<? extends DependentResource> dependentType) {
    if (name.isBlank()) {
      name = DependentResource.defaultNameFor(dependentType);
    }
    return name;
  }

  @SuppressWarnings("unused")
  private static <T> Configurator<T> configuratorFor(Class<T> instanceType,
      Reconciler<?> reconciler) {
    return instance -> configureFromAnnotatedReconciler(instance, reconciler);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void configureFromAnnotatedReconciler(Object instance, Reconciler<?> reconciler) {
    if (instance instanceof AnnotationConfigurable configurable) {
      final Class<? extends Annotation> configurationClass =
          (Class<? extends Annotation>) Utils.getFirstTypeArgumentFromSuperClassOrInterface(
              instance.getClass(), AnnotationConfigurable.class);
      final var configAnnotation = reconciler.getClass().getAnnotation(configurationClass);
      if (configAnnotation != null) {
        configurable.initFrom(configAnnotation);
      }
    }
  }
}

package io.javaoperatorsdk.operator.api.config;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.Utils.Configurator;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilters;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.retry.Retry;

import com.fasterxml.jackson.databind.ObjectMapper;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;

public class BaseConfigurationService extends AbstractConfigurationService {

  private static final String LOGGER_NAME = "Default ConfigurationService implementation";
  private static final Logger logger = LoggerFactory.getLogger(LOGGER_NAME);

  public BaseConfigurationService(Version version) {
    super(version);
  }

  public BaseConfigurationService(Version version, Cloner cloner, ObjectMapper mapper) {
    super(version, cloner, mapper, null);
  }

  public BaseConfigurationService(Version version, Cloner cloner) {
    super(version, cloner);
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

  @SuppressWarnings({"unchecked", "rawtypes"})
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
    Class<Reconciler<P>> reconcilerClass = (Class<Reconciler<P>>) reconciler.getClass();
    final var resourceClass = getResourceClassResolver().getResourceClass(reconcilerClass);

    final var name = ReconcilerUtils.getNameFor(reconciler);
    final var generationAware = valueOrDefault(
        annotation,
        io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::generationAwareEventProcessing,
        true);
    final var associatedReconcilerClass =
        ResolvedControllerConfiguration.getAssociatedReconcilerClassName(reconciler.getClass());

    final var context = Utils.contextFor(name);
    final Class<? extends Retry> retryClass = annotation.retry();
    final var retry = Utils.instantiateAndConfigureIfNeeded(retryClass, Retry.class,
        context, configuratorFor(Retry.class, reconciler));

    final Class<? extends RateLimiter> rateLimiterClass = annotation.rateLimiter();
    final var rateLimiter = Utils.instantiateAndConfigureIfNeeded(rateLimiterClass,
        RateLimiter.class, context, configuratorFor(RateLimiter.class, reconciler));

    final var reconciliationInterval = annotation.maxReconciliationInterval();
    long interval = -1;
    TimeUnit timeUnit = null;
    if (reconciliationInterval != null && reconciliationInterval.interval() > 0) {
      interval = reconciliationInterval.interval();
      timeUnit = reconciliationInterval.timeUnit();
    }

    final var config = new ResolvedControllerConfiguration<P>(
        resourceClass, name, generationAware,
        associatedReconcilerClass, retry, rateLimiter,
        ResolvedControllerConfiguration.getMaxReconciliationInterval(interval, timeUnit),
        Utils.instantiate(annotation.onAddFilter(), OnAddFilter.class, context),
        Utils.instantiate(annotation.onUpdateFilter(), OnUpdateFilter.class, context),
        Utils.instantiate(annotation.genericFilter(), GenericFilter.class, context),
        Set.of(valueOrDefault(annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::namespaces,
            DEFAULT_NAMESPACES_SET.toArray(String[]::new))),
        valueOrDefault(annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::finalizerName,
            Constants.NO_VALUE_SET),
        valueOrDefault(annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::labelSelector,
            Constants.NO_VALUE_SET),
        null,
        Utils.instantiate(annotation.itemStore(), ItemStore.class, context), this);

    ResourceEventFilter<P> answer = deprecatedEventFilter(annotation);
    config.setEventFilter(answer != null ? answer : ResourceEventFilters.passthrough());

    List<DependentResourceSpec> specs = dependentResources(annotation, config);
    config.setDependentResources(specs);

    return config;
  }

  @SuppressWarnings("unchecked")
  private static <P extends HasMetadata> ResourceEventFilter<P> deprecatedEventFilter(
      io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration annotation) {
    ResourceEventFilter<P> answer = null;

    Class<ResourceEventFilter<P>>[] filterTypes =
        (Class<ResourceEventFilter<P>>[]) valueOrDefault(annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::eventFilters,
            new Object[] {});
    for (var filterType : filterTypes) {
      try {
        ResourceEventFilter<P> filter = filterType.getConstructor().newInstance();

        if (answer == null) {
          answer = filter;
        } else {
          answer = answer.and(filter);
        }
      } catch (Exception e) {
        throw new IllegalArgumentException(e);
      }
    }
    return answer;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static List<DependentResourceSpec> dependentResources(
      io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration annotation,
      ControllerConfiguration<?> parent) {
    final var dependents =
        valueOrDefault(annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::dependents,
            new Dependent[] {});
    if (dependents.length == 0) {
      return Collections.emptyList();
    }

    final var specsMap = new LinkedHashMap<String, DependentResourceSpec>(dependents.length);
    for (Dependent dependent : dependents) {
      final Class<? extends DependentResource> dependentType = dependent.type();

      final var dependentName = getName(dependent.name(), dependentType);
      var spec = specsMap.get(dependentName);
      if (spec != null) {
        throw new IllegalArgumentException(
            "A DependentResource named '" + dependentName + "' already exists: " + spec);
      }

      final var name = parent.getName();

      var eventSourceName = dependent.useEventSourceWithName();
      eventSourceName = Constants.NO_VALUE_SET.equals(eventSourceName) ? null : eventSourceName;
      final var context = Utils.contextFor(name, dependentType, null);
      spec = new DependentResourceSpec(dependentType, dependentName,
          Set.of(dependent.dependsOn()),
          Utils.instantiate(dependent.readyPostcondition(), Condition.class, context),
          Utils.instantiate(dependent.reconcilePrecondition(), Condition.class, context),
          Utils.instantiate(dependent.deletePostcondition(), Condition.class, context),
          eventSourceName);
      specsMap.put(dependentName, spec);
    }
    return specsMap.values().stream().collect(Collectors.toUnmodifiableList());
  }

  protected boolean createIfNeeded() {
    return true;
  }

  @Override
  public boolean checkCRDAndValidateLocalModel() {
    return Utils.shouldCheckCRDAndValidateLocalModel();
  }

  private static <T> T valueOrDefault(
      io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration controllerConfiguration,
      Function<io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration, T> mapper,
      T defaultValue) {
    if (controllerConfiguration == null) {
      return defaultValue;
    } else {
      return mapper.apply(controllerConfiguration);
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
    if (instance instanceof AnnotationConfigurable) {
      AnnotationConfigurable configurable = (AnnotationConfigurable) instance;
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

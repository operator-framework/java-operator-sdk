package io.javaoperatorsdk.operator.api.config;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.Utils.Configurator;
import io.javaoperatorsdk.operator.api.config.Utils.Instantiator;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.AnnotationDependentResourceConfigurator;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilters;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.retry.Retry;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;

public abstract class ControllerConfigurationBuilder {

  private final Instantiator instantiator;

  public ControllerConfigurationBuilder() {
    this(Instantiator.DEFAULT);
  }

  protected ControllerConfigurationBuilder(Instantiator instantiator) {
    this.instantiator = instantiator;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public <P extends HasMetadata> ResolvedControllerConfiguration<P> createConfiguration(
      io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration annotation,
      Class<P> resourceClass, String name, String associatedReconcilerClass) {
    final var generationAware = valueOrDefault(
        annotation,
        io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::generationAwareEventProcessing,
        true);

    final Class<? extends Retry> retryClass = annotation.retry();
    final var retry = Utils.instantiateAndConfigureIfNeeded(retryClass, Retry.class,
        Utils.contextFor(name, null, null),
        configuratorFor(Retry.class), instantiator);

    final Class<? extends RateLimiter> rateLimiterClass = annotation.rateLimiter();
    final var rateLimiter = Utils.instantiateAndConfigureIfNeeded(rateLimiterClass,
        RateLimiter.class,
        Utils.contextFor(name, null, null),
        configuratorFor(RateLimiter.class), instantiator);

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
        Utils.instantiate(annotation.onAddFilter(), OnAddFilter.class,
            Utils.contextFor(name, null, null)),
        Utils.instantiate(annotation.onUpdateFilter(), OnUpdateFilter.class,
            Utils.contextFor(name, null, null)),
        Utils.instantiate(annotation.genericFilter(), GenericFilter.class,
            Utils.contextFor(name, null, null)),
        Set.of(valueOrDefault(annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::namespaces,
            DEFAULT_NAMESPACES_SET.toArray(String[]::new))),
        valueOrDefault(annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::finalizerName,
            Constants.NO_VALUE_SET),
        valueOrDefault(annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::labelSelector,
            Constants.NO_VALUE_SET));

    ResourceEventFilter<P> answer = deprecatedEventFilter(annotation, name);
    config.setEventFilter(answer != null ? answer : ResourceEventFilters.passthrough());

    List<DependentResourceSpec> specs = dependentResources(annotation, config);
    config.setDependentResources(specs);

    return config;
  }

  protected abstract <T> Configurator<T> configuratorFor(Class<T> typeOfObjectToConfigure);

  @SuppressWarnings("unchecked")
  private <P extends HasMetadata> ResourceEventFilter<P> deprecatedEventFilter(
      io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration annotation,
      String reconcilerName) {
    ResourceEventFilter<P> answer = null;

    Class<ResourceEventFilter<P>>[] filterTypes =
        (Class<ResourceEventFilter<P>>[]) valueOrDefault(annotation,
            io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration::eventFilters,
            new Object[] {});
    for (var filterType : filterTypes) {
      try {
        ResourceEventFilter<P> filter = Utils.instantiateAndConfigureIfNeeded(filterType,
            ResourceEventFilter.class,
            Utils.contextFor(reconcilerName, null, null),
            null, instantiator);

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
  protected List<DependentResourceSpec> dependentResources(
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
      final var dependentResource = Utils.instantiateAndConfigureIfNeeded(dependentType,
          DependentResource.class,
          Utils.contextFor(name, dependentType, Dependent.class),
          instance -> configureFromCustomAnnotation(instance, parent),
          instantiator);

      var eventSourceName = dependent.useEventSourceWithName();
      eventSourceName = Constants.NO_VALUE_SET.equals(eventSourceName) ? null : eventSourceName;
      final var context = Utils.contextFor(name, dependentType, null);
      spec = new DependentResourceSpec(dependentResource, dependentName,
          Set.of(dependent.dependsOn()),
          Utils.instantiate(dependent.readyPostcondition(), Condition.class, context),
          Utils.instantiate(dependent.reconcilePrecondition(), Condition.class, context),
          Utils.instantiate(dependent.deletePostcondition(), Condition.class, context),
          eventSourceName);
      specsMap.put(dependentName, spec);
    }
    return specsMap.values().stream().collect(Collectors.toUnmodifiableList());
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

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static <P extends HasMetadata> void configureFromCustomAnnotation(Object instance,
      ControllerConfiguration<P> parent) {
    if (instance instanceof AnnotationDependentResourceConfigurator) {
      AnnotationDependentResourceConfigurator configurator =
          (AnnotationDependentResourceConfigurator) instance;
      final Class<? extends Annotation> configurationClass =
          (Class<? extends Annotation>) Utils.getFirstTypeArgumentFromInterface(
              instance.getClass(), AnnotationDependentResourceConfigurator.class);
      final var configAnnotation = instance.getClass().getAnnotation(configurationClass);
      // always called even if the annotation is null so that implementations can provide default
      // values
      final var config = configurator.configFrom(configAnnotation, parent);
      configurator.configureWith(config);
    }
  }
}

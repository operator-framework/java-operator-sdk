package io.javaoperatorsdk.operator.api.config;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
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

@SuppressWarnings("rawtypes")
public class AnnotationControllerConfiguration<P extends HasMetadata>
    implements io.javaoperatorsdk.operator.api.config.ControllerConfiguration<P> {

  protected final Reconciler<P> reconciler;
  private final ControllerConfiguration annotation;
  private List<DependentResourceSpec> specs;
  private Class<P> resourceClass;

  public AnnotationControllerConfiguration(Reconciler<P> reconciler) {
    this.reconciler = reconciler;
    this.annotation = reconciler.getClass().getAnnotation(ControllerConfiguration.class);
    if (annotation == null) {
      throw new OperatorException(
          "Missing mandatory @" + ControllerConfiguration.class.getSimpleName() +
              " annotation for reconciler:  " + reconciler);
    }
  }

  @Override
  public String getName() {
    return ReconcilerUtils.getNameFor(reconciler);
  }

  @Override
  public String getFinalizerName() {
    if (annotation == null || annotation.finalizerName().isBlank()) {
      return ReconcilerUtils.getDefaultFinalizerName(getResourceClass());
    } else {
      final var finalizer = annotation.finalizerName();
      if (ReconcilerUtils.isFinalizerValid(finalizer)) {
        return finalizer;
      } else {
        throw new IllegalArgumentException(
            finalizer
                + " is not a valid finalizer. See https://kubernetes.io/docs/tasks/extend-kubernetes/custom-resources/custom-resource-definitions/#finalizers for details");
      }
    }
  }

  @Override
  public boolean isGenerationAware() {
    return valueOrDefault(
        annotation, ControllerConfiguration::generationAwareEventProcessing, true);
  }

  @Override
  public Set<String> getNamespaces() {
    return Set.of(valueOrDefault(annotation, ControllerConfiguration::namespaces,
        DEFAULT_NAMESPACES_SET.toArray(String[]::new)));
  }

  @Override
  @SuppressWarnings("unchecked")
  public Optional<ItemStore<P>> itemStore() {
    return Optional.ofNullable(
        Utils.instantiate(annotation.itemStore(), ItemStore.class,
            Utils.contextFor(this, null, null)));
  }

  @Override
  @SuppressWarnings("unchecked")
  public Class<P> getResourceClass() {
    if (resourceClass == null) {
      resourceClass =
          (Class<P>) Utils.getFirstTypeArgumentFromSuperClassOrInterface(reconciler.getClass(),
              Reconciler.class);
    }
    return resourceClass;
  }

  @Override
  public String getLabelSelector() {
    return valueOrDefault(annotation, ControllerConfiguration::labelSelector, "");
  }

  @Override
  public String getAssociatedReconcilerClassName() {
    return reconciler.getClass().getCanonicalName();
  }

  @SuppressWarnings("unchecked")
  @Override
  public ResourceEventFilter<P> getEventFilter() {
    ResourceEventFilter<P> answer = null;

    Class<ResourceEventFilter<P>>[] filterTypes =
        (Class<ResourceEventFilter<P>>[]) valueOrDefault(annotation,
            ControllerConfiguration::eventFilters, new Object[] {});
    if (filterTypes.length > 0) {
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
    }
    return answer != null ? answer : ResourceEventFilters.passthrough();
  }

  @Override
  public Optional<Duration> maxReconciliationInterval() {
    final var newConfig = annotation.maxReconciliationInterval();
    if (newConfig != null && newConfig.interval() > 0) {
      return Optional.of(Duration.of(newConfig.interval(), newConfig.timeUnit().toChronoUnit()));
    }
    return Optional.empty();
  }

  @Override
  public RateLimiter getRateLimiter() {
    final Class<? extends RateLimiter> rateLimiterClass = annotation.rateLimiter();
    return Utils.instantiateAndConfigureIfNeeded(rateLimiterClass, RateLimiter.class,
        Utils.contextFor(this, null, null), this::configureFromAnnotatedReconciler);
  }

  @Override
  public Retry getRetry() {
    final Class<? extends Retry> retryClass = annotation.retry();
    return Utils.instantiateAndConfigureIfNeeded(retryClass, Retry.class,
        Utils.contextFor(this, null, null), this::configureFromAnnotatedReconciler);
  }


  @SuppressWarnings("unchecked")
  private void configureFromAnnotatedReconciler(Object instance) {
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

  @SuppressWarnings("unchecked")
  private void configureFromCustomAnnotation(Object instance) {
    if (instance instanceof AnnotationDependentResourceConfigurator) {
      AnnotationDependentResourceConfigurator configurator =
          (AnnotationDependentResourceConfigurator) instance;
      final Class<? extends Annotation> configurationClass =
          (Class<? extends Annotation>) Utils.getFirstTypeArgumentFromInterface(
              instance.getClass(), AnnotationDependentResourceConfigurator.class);
      final var configAnnotation = instance.getClass().getAnnotation(configurationClass);
      // always called even if the annotation is null so that implementations can provide default
      // values
      final var config = configurator.configFrom(configAnnotation, this);
      configurator.configureWith(config);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Optional<OnAddFilter<P>> onAddFilter() {
    return Optional.ofNullable(
        Utils.instantiate(annotation.onAddFilter(), OnAddFilter.class,
            Utils.contextFor(this, null, null)));
  }

  @SuppressWarnings("unchecked")
  @Override
  public Optional<OnUpdateFilter<P>> onUpdateFilter() {
    return Optional.ofNullable(
        Utils.instantiate(annotation.onUpdateFilter(), OnUpdateFilter.class,
            Utils.contextFor(this, null, null)));
  }

  @SuppressWarnings("unchecked")
  @Override
  public Optional<GenericFilter<P>> genericFilter() {
    return Optional.ofNullable(
        Utils.instantiate(annotation.genericFilter(), GenericFilter.class,
            Utils.contextFor(this, null, null)));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public List<DependentResourceSpec> getDependentResources() {
    if (specs == null) {
      final var dependents =
          valueOrDefault(annotation, ControllerConfiguration::dependents, new Dependent[] {});
      if (dependents.length == 0) {
        specs = Collections.emptyList();
        return specs;
      }

      final var specsMap = new LinkedHashMap<String, DependentResourceSpec>(dependents.length);
      for (Dependent dependent : dependents) {
        final Class<? extends DependentResource> dependentType = dependent.type();

        final var name = getName(dependent, dependentType);
        var spec = specsMap.get(name);
        if (spec != null) {
          throw new IllegalArgumentException(
              "A DependentResource named '" + name + "' already exists: " + spec);
        }

        final var dependentResource = Utils.instantiateAndConfigureIfNeeded(dependentType,
            DependentResource.class,
            Utils.contextFor(this, dependentType, Dependent.class),
            this::configureFromCustomAnnotation);

        var eventSourceName = dependent.useEventSourceWithName();
        eventSourceName = Constants.NO_VALUE_SET.equals(eventSourceName) ? null : eventSourceName;
        final var context = Utils.contextFor(this, dependentType, null);
        spec = new DependentResourceSpec(dependentResource, name,
            Set.of(dependent.dependsOn()),
            Utils.instantiate(dependent.readyPostcondition(), Condition.class, context),
            Utils.instantiate(dependent.reconcilePrecondition(), Condition.class, context),
            Utils.instantiate(dependent.deletePostcondition(), Condition.class, context),
            eventSourceName);
        specsMap.put(name, spec);
      }

      specs = specsMap.values().stream().collect(Collectors.toUnmodifiableList());
    }
    return specs;
  }

  private String getName(Dependent dependent, Class<? extends DependentResource> dependentType) {
    var name = dependent.name();
    if (name.isBlank()) {
      name = DependentResource.defaultNameFor(dependentType);
    }
    return name;
  }

  public static <T> T valueOrDefault(
      ControllerConfiguration controllerConfiguration,
      Function<ControllerConfiguration, T> mapper,
      T defaultValue) {
    if (controllerConfiguration == null) {
      return defaultValue;
    } else {
      return mapper.apply(controllerConfiguration);
    }
  }

}

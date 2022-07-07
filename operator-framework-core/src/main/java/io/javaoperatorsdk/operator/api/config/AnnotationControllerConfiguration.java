package io.javaoperatorsdk.operator.api.config;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.VoidCondition;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilters;
import io.javaoperatorsdk.operator.processing.event.source.filter.EventFilter;
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
  public Optional<Duration> reconciliationMaxInterval() {
    if (annotation.reconciliationMaxInterval() != null) {
      if (annotation.reconciliationMaxInterval().interval() <= 0) {
        return Optional.empty();
      }
      return Optional.of(
          Duration.of(
              annotation.reconciliationMaxInterval().interval(),
              annotation.reconciliationMaxInterval().timeUnit().toChronoUnit()));
    } else {
      return io.javaoperatorsdk.operator.api.config.ControllerConfiguration.super.reconciliationMaxInterval();
    }
  }

  @Override
  public RateLimiter getRateLimiter() {
    final Class<? extends RateLimiter> rateLimiterClass = annotation.rateLimiter();
    return instantiateAndConfigureIfNeeded(rateLimiterClass, RateLimiter.class);
  }

  @Override
  public Retry getRetry() {
    final Class<? extends Retry> retryClass = annotation.retry();
    return instantiateAndConfigureIfNeeded(retryClass, Retry.class);
  }

  @Override
  public EventFilter<P> getFilter() {
    final Class<? extends EventFilter> filter = annotation.filter();
    return EventFilter.class.equals(filter) ? EventFilter.ACCEPTS_ALL
        : instantiateAndConfigureIfNeeded(filter, EventFilter.class);
  }

  @SuppressWarnings("unchecked")
  private <T> T instantiateAndConfigureIfNeeded(Class<? extends T> targetClass,
      Class<T> expectedType) {
    try {
      final Constructor<? extends T> constructor = targetClass.getDeclaredConstructor();
      constructor.setAccessible(true);
      final var instance = constructor.newInstance();
      if (instance instanceof AnnotationConfigurable) {
        AnnotationConfigurable configurable = (AnnotationConfigurable) instance;
        final Class<? extends Annotation> configurationClass =
            (Class<? extends Annotation>) Utils.getFirstTypeArgumentFromSuperClassOrInterface(
                targetClass, AnnotationConfigurable.class);
        final var configAnnotation = reconciler.getClass().getAnnotation(configurationClass);
        if (configAnnotation != null) {
          configurable.initFrom(configAnnotation);
        }
      }
      return instance;
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException
        | NoSuchMethodException e) {
      throw new OperatorException("Couldn't instantiate " + expectedType.getSimpleName() + " '"
          + targetClass.getName() + "' for '" + getName()
          + "' reconciler. You need to provide an accessible no-arg constructor.", e);
    }
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
        Object config = null;
        final Class<? extends DependentResource> dependentType = dependent.type();
        if (KubernetesDependentResource.class.isAssignableFrom(dependentType)) {
          config = createKubernetesResourceConfig(dependentType);
        }

        final var name = getName(dependent, dependentType);
        var spec = specsMap.get(name);
        if (spec != null) {
          throw new IllegalArgumentException(
              "A DependentResource named: " + name + " already exists: " + spec);
        }
        spec = new DependentResourceSpec(dependentType, config, name);
        spec.setDependsOn(Set.of(dependent.dependsOn()));
        addConditions(spec, dependent);
        specsMap.put(name, spec);
      }

      specs = specsMap.values().stream().collect(Collectors.toUnmodifiableList());
    }
    return specs;
  }

  @SuppressWarnings("unchecked")
  private void addConditions(DependentResourceSpec spec, Dependent dependent) {
    if (dependent.deletePostcondition() != VoidCondition.class) {
      spec.setDeletePostCondition(instantiateCondition(dependent.deletePostcondition()));
    }
    if (dependent.readyPostcondition() != VoidCondition.class) {
      spec.setReadyPostcondition(instantiateCondition(dependent.readyPostcondition()));
    }
    if (dependent.reconcilePrecondition() != VoidCondition.class) {
      spec.setReconcilePrecondition(instantiateCondition(dependent.reconcilePrecondition()));
    }
  }

  private Condition<?, ?> instantiateCondition(Class<? extends Condition> condition) {
    try {
      return condition.getDeclaredConstructor().newInstance();
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      throw new OperatorException(e);
    }
  }

  private String getName(Dependent dependent, Class<? extends DependentResource> dependentType) {
    var name = dependent.name();
    if (name.isBlank()) {
      name = DependentResource.defaultNameFor(dependentType);
    }
    return name;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object createKubernetesResourceConfig(Class<? extends DependentResource> dependentType) {

    Object config;
    final var kubeDependent = dependentType.getAnnotation(KubernetesDependent.class);

    var namespaces = getNamespaces();
    var configuredNS = false;
    String labelSelector = null;
    EventFilter filter = EventFilter.ACCEPTS_ALL;
    if (kubeDependent != null) {
      if (!Arrays.equals(KubernetesDependent.DEFAULT_NAMESPACES,
          kubeDependent.namespaces())) {
        namespaces = Set.of(kubeDependent.namespaces());
        configuredNS = true;
      }

      final var fromAnnotation = kubeDependent.labelSelector();
      labelSelector = Constants.NO_VALUE_SET.equals(fromAnnotation) ? null : fromAnnotation;

      final var kubeDependentName = KubernetesDependent.class.getSimpleName();
      final Class<? extends EventFilter> filterClass = kubeDependent.filter();
      if (!EventFilter.class.equals(filterClass)) {
        filter = instantiateAndConfigureIfNeeded(filterClass, EventFilter.class);
      }
    }

    config = new KubernetesDependentResourceConfig(namespaces, labelSelector, configuredNS, filter);

    return config;
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

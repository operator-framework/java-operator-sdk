package io.javaoperatorsdk.operator.api.config;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.config.eventsource.EventSourceSpec;
import io.javaoperatorsdk.operator.api.config.eventsource.GenericEventSourceSpec;
import io.javaoperatorsdk.operator.api.config.eventsource.InformerEventSourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.VoidCondition;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.javaoperatorsdk.operator.processing.event.rate.PeriodRateLimiter;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilters;
import io.javaoperatorsdk.operator.processing.event.source.filter.VoidGenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.VoidOnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.VoidOnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.VoidOnUpdateFilter;

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
    if (annotation.rateLimit() != null) {
      return new PeriodRateLimiter(Duration.of(annotation.rateLimit().refreshPeriod(),
          annotation.rateLimit().refreshPeriodTimeUnit().toChronoUnit()),
          annotation.rateLimit().limitForPeriod());
    } else {
      return io.javaoperatorsdk.operator.api.config.ControllerConfiguration.super.getRateLimiter();
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Optional<Predicate<P>> onAddFilter() {
    return (Optional<Predicate<P>>) createFilter(annotation.onAddFilter(), FilterType.onAdd,
        annotation.getClass().getSimpleName());
  }

  private enum FilterType {
    onAdd(VoidOnAddFilter.class), onUpdate(VoidOnUpdateFilter.class), onDelete(
        VoidOnDeleteFilter.class), generic(VoidGenericFilter.class);

    final Class<?> defaultValue;

    FilterType(Class<?> defaultValue) {
      this.defaultValue = defaultValue;
    }
  }

  private <T> Optional<T> createFilter(Class<T> filter, FilterType filterType, String origin) {
    if (filterType.defaultValue.equals(filter)) {
      return Optional.empty();
    } else {
      try {
        var instance = (T) filter.getDeclaredConstructor().newInstance();
        return Optional.of(instance);
      } catch (InstantiationException | IllegalAccessException | InvocationTargetException
          | NoSuchMethodException e) {
        throw new OperatorException(
            "Couldn't create " + filterType + " filter from " + filter.getName() + " class in "
                + origin + " for reconciler " + getName(),
            e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public Optional<BiPredicate<P, P>> onUpdateFilter() {
    return (Optional<BiPredicate<P, P>>) createFilter(annotation.onUpdateFilter(),
        FilterType.onUpdate, annotation.getClass().getSimpleName());
  }

  @SuppressWarnings("unchecked")
  @Override
  public Optional<Predicate<P>> genericFilter() {
    return (Optional<Predicate<P>>) createFilter(annotation.genericFilter(),
        FilterType.generic, annotation.getClass().getSimpleName());
  }

  @Override
  public List<EventSourceSpec> getEventSources() {
    var informerSpecs = Arrays
        .stream(annotation.eventSources().informers()).map(this::toInformerEventSourceSpec);
    var otherEventSources = Arrays
        .stream(annotation.eventSources().others()).map(this::toEventSourceSpec);
    return Stream.concat(informerSpecs, otherEventSources).collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  private EventSourceSpec toEventSourceSpec(EventSource eventSource) {
    return new GenericEventSourceSpec(eventSource.name(), eventSource.type());
  }

  @SuppressWarnings("unchecked")
  private InformerEventSourceSpec toInformerEventSourceSpec(Informer informer) {
    try {
      Set<String> namespaces = new HashSet<>();
      Collections.addAll(namespaces, informer.namespaces());
      SecondaryToPrimaryMapper secondaryToPrimaryMapper =
          informer.secondaryToPrimaryMapper().getConstructor().newInstance();
      PrimaryToSecondaryMapper primaryToSecondaryMapper = null;
      // todo unit test
      if (!informer.primaryToSecondaryMapper().equals(VoidPrimaryToSecondaryMapper.class)) {
        primaryToSecondaryMapper =
            informer.primaryToSecondaryMapper().getConstructor().newInstance();
      }
      return new InformerEventSourceSpec(informer.name(), informer.resourceType(),
          informer.labelSelector(), namespaces,
          informer.followNamespaceChanges(), secondaryToPrimaryMapper, primaryToSecondaryMapper);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException
        | NoSuchMethodException e) {
      throw new IllegalStateException(e);
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
        spec = new DependentResourceSpec(dependentType, config, name,
            Set.of(dependent.dependsOn()),
            instantiateConditionIfNotVoid(dependent.readyPostcondition()),
            instantiateConditionIfNotVoid(dependent.reconcilePrecondition()),
            instantiateConditionIfNotVoid(dependent.deletePostcondition()));
        specsMap.put(name, spec);
      }

      specs = specsMap.values().stream().collect(Collectors.toUnmodifiableList());
    }
    return specs;
  }

  private Condition<?, ?> instantiateConditionIfNotVoid(Class<? extends Condition> condition) {
    if (condition != VoidCondition.class) {
      try {
        return condition.getDeclaredConstructor().newInstance();
      } catch (InstantiationException
          | IllegalAccessException
          | InvocationTargetException
          | NoSuchMethodException e) {
        throw new OperatorException(e);
      }
    }
    return null;
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
    final var kubeDependent = dependentType.getAnnotation(
        io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent.class);

    var namespaces = getNamespaces();
    var configuredNS = false;
    String labelSelector = null;
    Predicate<? extends HasMetadata> onAddFilter = null;
    BiPredicate<? extends HasMetadata, ? extends HasMetadata> onUpdateFilter = null;
    BiPredicate<? extends HasMetadata, Boolean> onDeleteFilter = null;
    Predicate<? extends HasMetadata> genericFilter = null;
    if (kubeDependent != null) {
      if (!Arrays.equals(KubernetesDependent.DEFAULT_NAMESPACES,
          kubeDependent.namespaces())) {
        namespaces = Set.of(kubeDependent.namespaces());
        configuredNS = true;
      }

      final var fromAnnotation = kubeDependent.labelSelector();
      labelSelector = Constants.NO_VALUE_SET.equals(fromAnnotation) ? null : fromAnnotation;

      final var kubeDependentName = KubernetesDependent.class.getSimpleName();
      onAddFilter = createFilter(kubeDependent.onAddFilter(), FilterType.onAdd, kubeDependentName)
          .orElse(null);
      onUpdateFilter =
          createFilter(kubeDependent.onUpdateFilter(), FilterType.onUpdate, kubeDependentName)
              .orElse(null);
      onDeleteFilter =
          createFilter(kubeDependent.onDeleteFilter(), FilterType.onDelete, kubeDependentName)
              .orElse(null);
      genericFilter =
          createFilter(kubeDependent.genericFilter(), FilterType.generic, kubeDependentName)
              .orElse(null);
    }

    config =
        new KubernetesDependentResourceConfig(namespaces, labelSelector, configuredNS, onAddFilter,
            onUpdateFilter, onDeleteFilter, genericFilter);

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

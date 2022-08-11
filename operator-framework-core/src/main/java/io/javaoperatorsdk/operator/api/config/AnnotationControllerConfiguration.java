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
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilters;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.retry.Retry;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;

@SuppressWarnings("rawtypes")
public class AnnotationControllerConfiguration<P extends HasMetadata>
    implements io.javaoperatorsdk.operator.api.config.ControllerConfiguration<P> {

  private static final String CONTROLLER_CONFIG_ANNOTATION =
      ControllerConfiguration.class.getSimpleName();
  private static final String KUBE_DEPENDENT_NAME = KubernetesDependent.class.getSimpleName();

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
    return instantiateAndConfigureIfNeeded(rateLimiterClass, RateLimiter.class,
        CONTROLLER_CONFIG_ANNOTATION);
  }

  @Override
  public Retry getRetry() {
    final Class<? extends Retry> retryClass = annotation.retry();
    return instantiateAndConfigureIfNeeded(retryClass, Retry.class, CONTROLLER_CONFIG_ANNOTATION);
  }

  @SuppressWarnings("unchecked")
  protected <T> T instantiateAndConfigureIfNeeded(Class<? extends T> targetClass,
      Class<T> expectedType, String context) {
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
          + "' reconciler in " + context
          + ". You need to provide an accessible no-arg constructor.", e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Optional<OnAddFilter<P>> onAddFilter() {
    return (Optional<OnAddFilter<P>>) createFilter(annotation.onAddFilter(), OnAddFilter.class,
        CONTROLLER_CONFIG_ANNOTATION);
  }

  protected <T> Optional<? extends T> createFilter(Class<? extends T> filter, Class<T> defaultValue,
      String origin) {
    if (defaultValue.equals(filter)) {
      return Optional.empty();
    } else {
      return Optional.of(instantiateAndConfigureIfNeeded(filter, defaultValue, origin));
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public Optional<OnUpdateFilter<P>> onUpdateFilter() {
    return (Optional<OnUpdateFilter<P>>) createFilter(annotation.onUpdateFilter(),
        OnUpdateFilter.class, CONTROLLER_CONFIG_ANNOTATION);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Optional<GenericFilter<P>> genericFilter() {
    return (Optional<GenericFilter<P>>) createFilter(annotation.genericFilter(),
        GenericFilter.class, CONTROLLER_CONFIG_ANNOTATION);
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
              "A DependentResource named '" + name + "' already exists: " + spec);
        }
        final var context = "DependentResource of type '" + dependentType.getName() + "'";
        spec = new DependentResourceSpec(dependentType, config, name,
            Set.of(dependent.dependsOn()),
            instantiateConditionIfNotDefault(dependent.readyPostcondition(), context),
            instantiateConditionIfNotDefault(dependent.reconcilePrecondition(), context),
            instantiateConditionIfNotDefault(dependent.deletePostcondition(), context));
        specsMap.put(name, spec);
      }

      specs = specsMap.values().stream().collect(Collectors.toUnmodifiableList());
    }
    return specs;
  }

  protected Condition<?, ?> instantiateConditionIfNotDefault(Class<? extends Condition> condition,
      String context) {
    if (condition != Condition.class) {
      return instantiateAndConfigureIfNeeded(condition, Condition.class, context);
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
    final var kubeDependent = dependentType.getAnnotation(KubernetesDependent.class);

    var namespaces = getNamespaces();
    var configuredNS = false;
    String labelSelector = null;
    OnAddFilter<? extends HasMetadata> onAddFilter = null;
    OnUpdateFilter<? extends HasMetadata> onUpdateFilter = null;
    OnDeleteFilter<? extends HasMetadata> onDeleteFilter = null;
    GenericFilter<? extends HasMetadata> genericFilter = null;
    if (kubeDependent != null) {
      if (!Arrays.equals(KubernetesDependent.DEFAULT_NAMESPACES,
          kubeDependent.namespaces())) {
        namespaces = Set.of(kubeDependent.namespaces());
        configuredNS = true;
      }

      final var fromAnnotation = kubeDependent.labelSelector();
      labelSelector = Constants.NO_VALUE_SET.equals(fromAnnotation) ? null : fromAnnotation;


      final var context =
          KUBE_DEPENDENT_NAME + " annotation on " + dependentType.getName() + " DependentResource";
      onAddFilter = createFilter(kubeDependent.onAddFilter(), OnAddFilter.class, context)
          .orElse(null);
      onUpdateFilter =
          createFilter(kubeDependent.onUpdateFilter(), OnUpdateFilter.class, context)
              .orElse(null);
      onDeleteFilter =
          createFilter(kubeDependent.onDeleteFilter(), OnDeleteFilter.class, context)
              .orElse(null);
      genericFilter =
          createFilter(kubeDependent.genericFilter(), GenericFilter.class, context)
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

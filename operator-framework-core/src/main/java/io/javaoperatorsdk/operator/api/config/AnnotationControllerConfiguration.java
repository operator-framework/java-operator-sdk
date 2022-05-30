package io.javaoperatorsdk.operator.api.config;

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
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilters;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;

@SuppressWarnings("rawtypes")
public class AnnotationControllerConfiguration<R extends HasMetadata>
    implements io.javaoperatorsdk.operator.api.config.ControllerConfiguration<R> {

  protected final Reconciler<R> reconciler;
  private final ControllerConfiguration annotation;
  private List<DependentResourceSpec> specs;

  public AnnotationControllerConfiguration(Reconciler<R> reconciler) {
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
  public Class<R> getResourceClass() {
    return (Class<R>) Utils.getFirstTypeArgumentFromInterface(reconciler.getClass());
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
  public ResourceEventFilter<R> getEventFilter() {
    ResourceEventFilter<R> answer = null;

    Class<ResourceEventFilter<R>>[] filterTypes =
        (Class<ResourceEventFilter<R>>[]) valueOrDefault(annotation,
            ControllerConfiguration::eventFilters, new Object[] {});
    if (filterTypes.length > 0) {
      for (var filterType : filterTypes) {
        try {
          ResourceEventFilter<R> filter = filterType.getConstructor().newInstance();

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
        // todo tests
        addConditions(spec,dependent);
        specsMap.put(name, spec);
      }

      specs = specsMap.values().stream().collect(Collectors.toUnmodifiableList());
    }
    return specs;
  }

  @SuppressWarnings("unchecked")
  private void addConditions(DependentResourceSpec spec, Dependent dependent) {
      if (dependent.deletePostCondition() != VoidCondition.class) {
        spec.setDeletePostCondition(instantiateCondition(dependent.deletePostCondition()));
      }
      if (dependent.readyCondition() != VoidCondition.class) {
        spec.setReadyCondition(instantiateCondition(dependent.readyCondition()));
      }
      if (dependent.reconcileCondition() != VoidCondition.class) {
        spec.setReconcileCondition(instantiateCondition(dependent.reconcileCondition()));
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

  private Object createKubernetesResourceConfig(Class<? extends DependentResource> dependentType) {
    Object config;
    final var kubeDependent = dependentType.getAnnotation(KubernetesDependent.class);

    var namespaces = getNamespaces();
    var configuredNS = false;
    if (kubeDependent != null && !Arrays.equals(KubernetesDependent.DEFAULT_NAMESPACES,
        kubeDependent.namespaces())) {
      namespaces = Set.of(kubeDependent.namespaces());
      configuredNS = true;
    }

    String labelSelector = null;
    if (kubeDependent != null) {
      final var fromAnnotation = kubeDependent.labelSelector();
      labelSelector = Constants.NO_VALUE_SET.equals(fromAnnotation) ? null : fromAnnotation;
    }

    config =
        new KubernetesDependentResourceConfig(namespaces, labelSelector, configuredNS);
    return config;
  }
}

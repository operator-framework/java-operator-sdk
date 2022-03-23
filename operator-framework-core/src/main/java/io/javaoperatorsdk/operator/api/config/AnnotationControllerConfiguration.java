package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilters;

public class AnnotationControllerConfiguration<R extends HasMetadata>
    implements io.javaoperatorsdk.operator.api.config.ControllerConfiguration<R> {

  protected final Reconciler<R> reconciler;
  private final ControllerConfiguration annotation;
  private Map<String, DependentResourceSpec<?, ?>> specs;

  public AnnotationControllerConfiguration(Reconciler<R> reconciler) {
    this.reconciler = reconciler;
    this.annotation = reconciler.getClass().getAnnotation(ControllerConfiguration.class);
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
    return Set.of(valueOrDefault(annotation, ControllerConfiguration::namespaces, new String[] {}));
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
  public Map<String, DependentResourceSpec<?, ?>> getDependentResources() {
    if (specs == null) {
      final var dependents =
          valueOrDefault(annotation, ControllerConfiguration::dependents, new Dependent[] {});
      if (dependents.length == 0) {
        return Collections.emptyMap();
      }

      specs = new HashMap<>(dependents.length);
      for (Dependent dependent : dependents) {
        Object config = null;
        final Class<? extends DependentResource> dependentType = dependent.type();
        if (KubernetesDependentResource.class.isAssignableFrom(dependentType)) {
          final var kubeDependent = dependentType.getAnnotation(KubernetesDependent.class);
          final var namespaces =
              Utils.valueOrDefault(
                  kubeDependent,
                  KubernetesDependent::namespaces,
                  this.getNamespaces().toArray(new String[0]));
          final var labelSelector =
              Utils.valueOrDefault(kubeDependent, KubernetesDependent::labelSelector, null);
          final var addOwnerReference =
              Utils.valueOrDefault(
                  kubeDependent,
                  KubernetesDependent::addOwnerReference,
                  KubernetesDependent.ADD_OWNER_REFERENCE_DEFAULT);
          config =
              new KubernetesDependentResourceConfig(addOwnerReference, namespaces, labelSelector);
        }
        var name = dependent.name();
        if (name.isBlank()) {
          name = DependentResource.defaultNameFor(dependentType);
        }
        final DependentResourceSpec<?, ?> spec = specs.get(name);
        if (spec != null) {
          throw new IllegalArgumentException(
              "A DependentResource named: " + name + " already exists: " + spec);
        }
        specs.put(name, new DependentResourceSpec(dependentType, config, name));
      }
    }
    return Collections.unmodifiableMap(specs);
  }
}

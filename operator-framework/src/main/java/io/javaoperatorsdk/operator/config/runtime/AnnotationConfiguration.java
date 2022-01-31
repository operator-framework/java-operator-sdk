package io.javaoperatorsdk.operator.config.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.Dependent;
import io.javaoperatorsdk.operator.api.config.DependentResource;
import io.javaoperatorsdk.operator.api.config.DependentResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.KubernetesDependent;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.dependent.KubernetesDependentResourceConfiguration;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilters;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerConfiguration;

@SuppressWarnings("rawtypes")
public class AnnotationConfiguration<R extends HasMetadata>
    implements io.javaoperatorsdk.operator.api.config.ControllerConfiguration<R> {

  private final Reconciler<R> reconciler;
  private final ControllerConfiguration annotation;
  private ConfigurationService service;
  private List<DependentResourceConfiguration> dependentConfigurations;

  public AnnotationConfiguration(Reconciler<R> reconciler) {
    this.reconciler = reconciler;
    this.annotation = reconciler.getClass().getAnnotation(ControllerConfiguration.class);
  }

  @Override
  public String getName() {
    return ReconcilerUtils.getNameFor(reconciler);
  }

  @Override
  public String getFinalizer() {
    if (annotation == null || annotation.finalizerName().isBlank()) {
      return ReconcilerUtils.getDefaultFinalizerName(getResourceClass());
    } else {
      final var finalizer = annotation.finalizerName();
      if (ReconcilerUtils.isFinalizerValid(finalizer)) {
        return finalizer;
      } else {
        throw new IllegalArgumentException(finalizer
            + " is not a valid finalizer. See https://kubernetes.io/docs/tasks/extend-kubernetes/custom-resources/custom-resource-definitions/#finalizers for details");
      }
    }
  }

  @Override
  public boolean isGenerationAware() {
    return valueOrDefault(annotation, ControllerConfiguration::generationAwareEventProcessing,
        true);
  }

  @Override
  public Class<R> getResourceClass() {
    return RuntimeControllerMetadata.getResourceClass(reconciler);
  }

  @Override
  public Set<String> getNamespaces() {
    return Set.of(valueOrDefault(annotation, ControllerConfiguration::namespaces, new String[] {}));
  }

  @Override
  public String getLabelSelector() {
    return valueOrDefault(annotation, ControllerConfiguration::labelSelector, "");
  }

  @Override
  public ConfigurationService getConfigurationService() {
    return service;
  }

  @Override
  public void setConfigurationService(ConfigurationService service) {
    this.service = service;
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
            ControllerConfiguration::eventFilters,
            new Object[] {});
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
    return answer != null
        ? answer
        : ResourceEventFilters.passthrough();
  }

  @Override
  public List<DependentResourceConfiguration> getDependentResources() {
    if (dependentConfigurations == null) {
      final var dependents = valueOrDefault(annotation, ControllerConfiguration::dependents,
          new Dependent[]{});
      if (dependents.length > 0) {
        dependentConfigurations = new ArrayList<>(dependents.length);
        for (Dependent dependent : dependents) {
          final Class<? extends DependentResource> dependentType = dependent.type();
          final var resourceType = dependent.resourceType();

          if (HasMetadata.class.isAssignableFrom(resourceType)) {
            final var kubeDependent = dependentType.getAnnotation(KubernetesDependent.class);
            final var namespaces =
                valueOrDefault(kubeDependent, KubernetesDependent::namespaces, new String[]{});
            final var labelSelector =
                valueOrDefault(kubeDependent, KubernetesDependent::labelSelector, null);
            final var owned = valueOrDefault(kubeDependent, KubernetesDependent::owned,
                KubernetesDependent.OWNED_DEFAULT);
            final var skipIfUnchanged =
                valueOrDefault(kubeDependent, KubernetesDependent::skipUpdateIfUnchanged,
                    KubernetesDependent.SKIP_UPDATE_DEFAULT);
            final var configuration = InformerConfiguration.from(service, resourceType)
                .withLabelSelector(labelSelector)
                .skippingEventPropagationIfUnchanged(skipIfUnchanged)
                .withNamespaces(namespaces)
                .build();

            dependentConfigurations.add(
                KubernetesDependentResourceConfiguration.from(configuration, owned, dependentType));
          } else {
            dependentConfigurations.add(new DependentResourceConfiguration() {
              @Override
              public Class<? extends DependentResource> getDependentResourceClass() {
                return dependentType;
              }

              @Override
              public Class getResourceClass() {
                return resourceType;
              }
            });
          }
        }
      } else {
        dependentConfigurations = Collections.emptyList();
      }
    }
    return dependentConfigurations;
  }

  private static <C, T> T valueOrDefault(C annotation, Function<C, T> mapper, T defaultValue) {
    return annotation == null ? defaultValue : mapper.apply(annotation);
  }
}


package io.javaoperatorsdk.operator.config.runtime;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.dependent.DefaultDependentResourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceConfiguration.DEFAULT_PRIMARIES_RETRIEVER;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceConfiguration.DEFAULT_SECONDARY_IDENTIFIER;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilters;

public class AnnotationConfiguration<R extends HasMetadata>
    implements io.javaoperatorsdk.operator.api.config.ControllerConfiguration<R> {

  private final Reconciler<R> reconciler;
  private final ControllerConfiguration annotation;
  private ConfigurationService service;
  private List<DependentResource> dependents;

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
      return ReconcilerUtils.getDefaultFinalizerName(getResourceTypeName());
    } else {
      return annotation.finalizerName();
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
  public ResourceEventFilter<R, io.javaoperatorsdk.operator.api.config.ControllerConfiguration<R>> getEventFilter() {
    ResourceEventFilter<R, io.javaoperatorsdk.operator.api.config.ControllerConfiguration<R>> answer =
        null;

    var filterTypes =
        (Class<ResourceEventFilter<R, io.javaoperatorsdk.operator.api.config.ControllerConfiguration<R>>>[]) valueOrDefault(
            annotation, ControllerConfiguration::eventFilters, new Class[] {});
    if (filterTypes.length > 0) {
      for (var filterType : filterTypes) {
        try {
          var filter = filterType.getConstructor().newInstance();

          if (answer == null) {
            answer = filter;
          } else {
            answer = filter.and(filter);
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
    return answer != null
        ? answer
        : ResourceEventFilters.passthrough();
  }

  @Override
  public List<? extends DependentResource> getDependentResources() {
    if (dependents == null) {
      final var dependentConfigs = valueOrDefault(annotation,
          ControllerConfiguration::dependents, new DependentResourceConfiguration[] {});
      if (dependentConfigs.length > 0) {
        dependents = new ArrayList<>(dependentConfigs.length);
        for (DependentResourceConfiguration dependentConfig : dependentConfigs) {
          final var creatable = dependentConfig.creatable();
          final var updatable = dependentConfig.updatable();
          final var owned = dependentConfig.owned();

          final var resourceType = dependentConfig.resourceType();
          final var crdName = CustomResource.getCRDName(resourceType);
          final var namespaces = Set.of(
              valueOrDefault(dependentConfig, DependentResourceConfiguration::namespaces,
                  new String[] {}));
          final var labelSelector = dependentConfig.labelSelector();

          final PrimaryResourcesRetriever primariesMapper =
              valueIfPresentOrNull(
                  dependentConfig, DependentResourceConfiguration::associatedPrimariesRetriever,
                  DEFAULT_PRIMARIES_RETRIEVER.class);
          final AssociatedSecondaryIdentifier secondaryMapper =
              valueIfPresentOrNull(
                  dependentConfig, DependentResourceConfiguration::associatedSecondaryIdentifier,
                  DEFAULT_SECONDARY_IDENTIFIER.class);


          final DefaultDependentResourceConfiguration configuration =
              new DefaultDependentResourceConfiguration(
                  crdName, resourceType, namespaces, labelSelector, service, creatable, updatable,
                  owned, primariesMapper, secondaryMapper,
                  dependentConfig.skipUpdateIfUnchanged());

          final var builder =
              valueIfPresentOrNull(dependentConfig, DependentResourceConfiguration::builder,
                  DependentResourceConfiguration.DEFAULT_BUILDER.class);
          final var updater =
              valueIfPresentOrNull(dependentConfig, DependentResourceConfiguration::updater,
                  DependentResourceConfiguration.DEFAULT_UPDATER.class);
          final var fetcher =
              valueIfPresentOrNull(dependentConfig, DependentResourceConfiguration::fetcher,
                  DependentResourceConfiguration.DEFAULT_FETCHER.class);

          final var dependent = new DependentResource(configuration, builder, updater, fetcher);
          dependents.add(dependent);
        }
      } else {
        dependents = Collections.emptyList();
      }
    }
    return dependents;
  }

  private static <C, T> T valueOrDefault(C annotation, Function<C, T> mapper, T defaultValue) {
    return annotation == null ? defaultValue : mapper.apply(annotation);
  }

  private static <T> T valueIfPresentOrNull(DependentResourceConfiguration annotation,
      Function<DependentResourceConfiguration, Class<? extends T>> mapper,
      Class<? extends T> defaultValue) {
    if (annotation == null) {
      return null;
    }

    final var value = mapper.apply(annotation);
    if (defaultValue.equals(value)) {
      return null;
    }
    try {
      return value.getConstructor().newInstance();
    } catch (InstantiationException | NoSuchMethodException | InvocationTargetException
        | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}


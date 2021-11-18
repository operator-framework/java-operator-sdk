package io.javaoperatorsdk.operator.config.runtime;

import java.util.Set;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.event.internal.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.internal.ResourceEventFilters;

public class AnnotationConfiguration<R extends HasMetadata>
    implements io.javaoperatorsdk.operator.api.config.ControllerConfiguration<R> {

  private final Reconciler<R> reconciler;
  private final ControllerConfiguration annotation;
  private ConfigurationService service;

  public AnnotationConfiguration(Reconciler<R> reconciler) {
    this.reconciler = reconciler;
    this.annotation = reconciler.getClass().getAnnotation(ControllerConfiguration.class);
  }

  @Override
  public String getName() {
    return ControllerUtils.getNameFor(reconciler);
  }

  @Override
  public String getFinalizer() {
    if (annotation == null || annotation.finalizerName().isBlank()) {
      return ControllerUtils.getDefaultFinalizerName(getCRDName());
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
    return RuntimeControllerMetadata.getCustomResourceClass(reconciler);
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

  public static <T> T valueOrDefault(ControllerConfiguration controllerConfiguration,
      Function<ControllerConfiguration, T> mapper,
      T defaultValue) {
    if (controllerConfiguration == null) {
      return defaultValue;
    } else {
      return mapper.apply(controllerConfiguration);
    }
  }
}


package io.javaoperatorsdk.operator.config.runtime;

import java.util.Set;
import java.util.function.Function;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEventFilters;

public class AnnotationConfiguration<R extends CustomResource>
    implements ControllerConfiguration<R> {

  private final ResourceController<R> controller;
  private final Controller annotation;
  private ConfigurationService service;

  public AnnotationConfiguration(ResourceController<R> controller) {
    this.controller = controller;
    this.annotation = controller.getClass().getAnnotation(Controller.class);
  }

  @Override
  public String getName() {
    return ControllerUtils.getNameFor(controller);
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
    return valueOrDefault(annotation, Controller::generationAwareEventProcessing, true);
  }

  @Override
  public Class<R> getCustomResourceClass() {
    return RuntimeControllerMetadata.getCustomResourceClass(controller);
  }

  @Override
  public Set<String> getNamespaces() {
    return Set.of(valueOrDefault(annotation, Controller::namespaces, new String[] {}));
  }

  @Override
  public String getLabelSelector() {
    return valueOrDefault(annotation, Controller::labelSelector, "");
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
  public String getAssociatedControllerClassName() {
    return controller.getClass().getCanonicalName();
  }

  @SuppressWarnings("unchecked")
  @Override
  public CustomResourceEventFilter<R> getEventFilter() {
    CustomResourceEventFilter<R> answer = null;

    Class<CustomResourceEventFilter<R>>[] filterTypes =
        (Class<CustomResourceEventFilter<R>>[]) valueOrDefault(annotation, Controller::eventFilters,
            new Object[] {});
    if (filterTypes.length > 0) {
      for (var filterType : filterTypes) {
        try {
          CustomResourceEventFilter<R> filter = filterType.getConstructor().newInstance();

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
        : CustomResourceEventFilters.passthrough();
  }

  public static <T> T valueOrDefault(Controller controller, Function<Controller, T> mapper,
      T defaultValue) {
    if (controller == null) {
      return defaultValue;
    } else {
      return mapper.apply(controller);
    }
  }
}


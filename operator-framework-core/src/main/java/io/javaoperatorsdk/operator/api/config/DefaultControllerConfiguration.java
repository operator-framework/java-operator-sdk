package io.javaoperatorsdk.operator.api.config;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;

public class DefaultControllerConfiguration<R extends HasMetadata>
    extends DefaultResourceConfiguration<R, ControllerConfiguration<R>>
    implements ControllerConfiguration<R> {

  private final String associatedControllerClassName;
  private final String name;
  private final String finalizer;
  private final RetryConfiguration retryConfiguration;
  private final ResourceEventFilter<R, ControllerConfiguration<R>> resourceEventFilter;
  private final boolean generationAware;

  public DefaultControllerConfiguration(
      String associatedControllerClassName,
      String name,
      String resourceName,
      String finalizer,
      boolean generationAware,
      Set<String> namespaces,
      RetryConfiguration retryConfiguration,
      String labelSelector,
      ResourceEventFilter<R, ControllerConfiguration<R>> resourceEventFilter,
      Class<R> resourceClass,
      ConfigurationService service) {
    super(resourceName, resourceClass, namespaces, labelSelector, service);
    this.associatedControllerClassName = associatedControllerClassName;
    this.name = name;
    this.finalizer = finalizer;
    this.generationAware = generationAware;
    this.retryConfiguration =
        retryConfiguration == null
            ? ControllerConfiguration.super.getRetryConfiguration()
            : retryConfiguration;
    this.resourceEventFilter = resourceEventFilter;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getFinalizer() {
    return finalizer;
  }

  @Override
  public String getAssociatedReconcilerClassName() {
    return associatedControllerClassName;
  }

  @Override
  public RetryConfiguration getRetryConfiguration() {
    return retryConfiguration;
  }

  @Override
  public boolean isGenerationAware() {
    return generationAware;
  }

  @Override
  public ResourceEventFilter<R, ControllerConfiguration<R>> getEventFilter() {
    return resourceEventFilter;
  }

  @Override
  protected String identifierForException() {
    return "'" + name + "' ControllerConfiguration";
  }
}

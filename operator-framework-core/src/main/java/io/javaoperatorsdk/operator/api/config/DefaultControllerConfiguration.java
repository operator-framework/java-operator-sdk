package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;

public class DefaultControllerConfiguration<R extends HasMetadata>
    extends DefaultResourceConfiguration<R>
    implements ControllerConfiguration<R> {

  private final String associatedControllerClassName;
  private final String name;
  private final String crdName;
  private final String finalizer;
  private final boolean generationAware;
  private final RetryConfiguration retryConfiguration;
  private final ResourceEventFilter<R> resourceEventFilter;
  private final Map<String, DependentResourceSpec<?, ?>> dependents;
  private final Duration reconciliationMaxInterval;

  // NOSONAR constructor is meant to provide all information
  public DefaultControllerConfiguration(
      String associatedControllerClassName,
      String name,
      String crdName,
      String finalizer,
      boolean generationAware,
      Set<String> namespaces,
      RetryConfiguration retryConfiguration,
      String labelSelector,
      ResourceEventFilter<R> resourceEventFilter,
      Class<R> resourceClass,
      Duration reconciliationMaxInterval,
      Map<String, DependentResourceSpec<?, ?>> dependents) {
    super(labelSelector, resourceClass, namespaces);
    this.associatedControllerClassName = associatedControllerClassName;
    this.name = name;
    this.crdName = crdName;
    this.finalizer = finalizer;
    this.generationAware = generationAware;
    this.reconciliationMaxInterval = reconciliationMaxInterval;
    this.retryConfiguration =
        retryConfiguration == null
            ? ControllerConfiguration.super.getRetryConfiguration()
            : retryConfiguration;
    this.resourceEventFilter = resourceEventFilter;

    this.dependents = dependents != null ? dependents : Collections.emptyMap();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getResourceTypeName() {
    return crdName;
  }

  @Override
  public String getFinalizerName() {
    return finalizer;
  }

  @Override
  public boolean isGenerationAware() {
    return generationAware;
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
  public ResourceEventFilter<R> getEventFilter() {
    return resourceEventFilter;
  }

  @Override
  public Map<String, DependentResourceSpec<?, ?>> getDependentResources() {
    return dependents;
  }

  @Override
  public Optional<Duration> reconciliationMaxInterval() {
    return Optional.ofNullable(reconciliationMaxInterval);
  }
}

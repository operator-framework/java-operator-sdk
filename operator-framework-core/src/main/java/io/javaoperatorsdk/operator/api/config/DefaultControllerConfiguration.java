package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.processing.event.rate.LinearRateLimiter;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.retry.Retry;

@SuppressWarnings("rawtypes")
public class DefaultControllerConfiguration<R extends HasMetadata>
    extends DefaultResourceConfiguration<R>
    implements ControllerConfiguration<R> {

  private final String associatedControllerClassName;
  private final String name;
  private final String crdName;
  private final String finalizer;
  private final boolean generationAware;
  private final Retry retry;
  private final ResourceEventFilter<R> resourceEventFilter;
  private final List<DependentResourceSpec> dependents;
  private final Duration reconciliationMaxInterval;
  private final RateLimiter rateLimiter;

  // NOSONAR constructor is meant to provide all information
  public DefaultControllerConfiguration(
      String associatedControllerClassName,
      String name,
      String crdName,
      String finalizer,
      boolean generationAware,
      Set<String> namespaces,
      Retry retry,
      String labelSelector,
      ResourceEventFilter<R> resourceEventFilter,
      Class<R> resourceClass,
      Duration reconciliationMaxInterval,
      OnAddFilter<R> onAddFilter,
      OnUpdateFilter<R> onUpdateFilter,
      GenericFilter<R> genericFilter,
      RateLimiter rateLimiter,
      List<DependentResourceSpec> dependents,
      ItemStore<R> itemStore) {
    super(labelSelector, resourceClass, onAddFilter, onUpdateFilter, genericFilter, namespaces,
        itemStore);
    this.associatedControllerClassName = associatedControllerClassName;
    this.name = name;
    this.crdName = crdName;
    this.finalizer = finalizer;
    this.generationAware = generationAware;
    this.reconciliationMaxInterval = reconciliationMaxInterval;
    this.retry =
        retry == null
            ? ControllerConfiguration.super.getRetry()
            : retry;
    this.resourceEventFilter = resourceEventFilter;
    this.rateLimiter =
        rateLimiter != null ? rateLimiter : LinearRateLimiter.deactivatedRateLimiter();
    this.dependents = dependents != null ? dependents : Collections.emptyList();
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
  public Retry getRetry() {
    return retry;
  }

  @Override
  public ResourceEventFilter<R> getEventFilter() {
    return resourceEventFilter;
  }

  @Override
  public List<DependentResourceSpec> getDependentResources() {
    return dependents;
  }

  @Override
  public Optional<Duration> maxReconciliationInterval() {
    return Optional.ofNullable(reconciliationMaxInterval);
  }

  @Override
  public RateLimiter getRateLimiter() {
    return rateLimiter;
  }

}

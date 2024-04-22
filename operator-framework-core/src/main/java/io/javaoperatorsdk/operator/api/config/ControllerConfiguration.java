package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval;
import io.javaoperatorsdk.operator.processing.event.rate.LinearRateLimiter;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilters;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.GradualRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;

public interface ControllerConfiguration<P extends HasMetadata> extends ResourceConfiguration<P> {

  boolean DEFAULT_RECONCILER_RESOURCES_MARKED_FOR_DELETION = false;


  @SuppressWarnings("rawtypes")
  RateLimiter DEFAULT_RATE_LIMITER = LinearRateLimiter.deactivatedRateLimiter();
  /**
   * Will use the controller name as fieldManager if set.
   */
  String CONTROLLER_NAME_AS_FIELD_MANAGER = "use_controller_name";

  default String getName() {
    return ensureValidName(null, getAssociatedReconcilerClassName());
  }

  default String getFinalizerName() {
    return ReconcilerUtils.getDefaultFinalizerName(getResourceClass());
  }

  static String ensureValidName(String name, String reconcilerClassName) {
    return name != null ? name : ReconcilerUtils.getDefaultReconcilerName(reconcilerClassName);
  }

  static String ensureValidFinalizerName(String finalizer, String resourceTypeName) {
    if (finalizer != null && !finalizer.isBlank()) {
      if (ReconcilerUtils.isFinalizerValid(finalizer)) {
        return finalizer;
      } else {
        throw new IllegalArgumentException(
            finalizer
                + " is not a valid finalizer. See https://kubernetes.io/docs/tasks/extend-kubernetes/custom-resources/custom-resource-definitions/#finalizers for details");
      }
    } else {
      return ReconcilerUtils.getDefaultFinalizerName(resourceTypeName);
    }
  }

  default boolean isGenerationAware() {
    return true;
  }

  String getAssociatedReconcilerClassName();

  default Retry getRetry() {
    final var configuration = getRetryConfiguration();
    return !RetryConfiguration.DEFAULT.equals(configuration)
        ? GenericRetry.fromConfiguration(configuration)
        : GenericRetry.DEFAULT; // NOSONAR
  }

  /**
   * Use {@link #getRetry()} instead.
   *
   * @return configuration for retry.
   * @deprecated provide your own {@link Retry} implementation or use the {@link GradualRetry}
   *             annotation instead
   */
  @Deprecated(forRemoval = true)
  default RetryConfiguration getRetryConfiguration() {
    return RetryConfiguration.DEFAULT;
  }

  @SuppressWarnings("rawtypes")
  default RateLimiter getRateLimiter() {
    return DEFAULT_RATE_LIMITER;
  }

  /**
   * Allow controllers to filter events before they are passed to the
   * {@link io.javaoperatorsdk.operator.processing.event.EventHandler}.
   *
   * <p>
   * Resource event filters only applies on events of the main custom resource. Not on events from
   * other event sources nor the periodic events.
   * </p>
   *
   * @return filter
   * @deprecated use {@link ResourceConfiguration#onAddFilter()},
   *             {@link ResourceConfiguration#onUpdateFilter()} or
   *             {@link ResourceConfiguration#genericFilter()} instead
   */
  @Deprecated(forRemoval = true)
  default ResourceEventFilter<P> getEventFilter() {
    return ResourceEventFilters.passthrough();
  }

  @SuppressWarnings("rawtypes")
  default List<DependentResourceSpec> getDependentResources() {
    return Collections.emptyList();
  }

  default Optional<Duration> maxReconciliationInterval() {
    return Optional.of(Duration.ofHours(MaxReconciliationInterval.DEFAULT_INTERVAL));
  }

  @SuppressWarnings("unused")
  ConfigurationService getConfigurationService();

  @SuppressWarnings("unchecked")
  @Override
  default Class<P> getResourceClass() {
    // note that this implementation at the end not used within the boundaries of the core
    // framework, should be removed in the future, (and marked as an API changed, or behavior
    // change)
    return (Class<P>) Utils.getFirstTypeArgumentFromSuperClassOrInterface(getClass(),
        ControllerConfiguration.class);
  }

  @SuppressWarnings("unused")
  default Set<String> getEffectiveNamespaces() {
    return ResourceConfiguration.super.getEffectiveNamespaces(getConfigurationService());
  }

  /**
   * Retrieves the name used to assign as field manager for
   * <a href="https://kubernetes.io/docs/reference/using-api/server-side-apply/">Server-Side
   * Apply</a> (SSA) operations. If unset, the sanitized controller name will be used.
   *
   * @return the name used as field manager for SSA operations
   */
  default String fieldManager() {
    return getName();
  }

  default boolean reconcileResourcesMarkedForDeletion() {
    return DEFAULT_RECONCILER_RESOURCES_MARKED_FOR_DELETION;
  }

}

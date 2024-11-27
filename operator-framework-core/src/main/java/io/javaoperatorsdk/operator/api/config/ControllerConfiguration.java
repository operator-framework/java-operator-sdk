package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.config.workflow.WorkflowSpec;
import io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval;
import io.javaoperatorsdk.operator.processing.event.rate.LinearRateLimiter;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;

public interface ControllerConfiguration<P extends HasMetadata> extends ResourceConfiguration<P> {

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
    return GenericRetry.DEFAULT;
  }

  @SuppressWarnings("rawtypes")
  default RateLimiter getRateLimiter() {
    return DEFAULT_RATE_LIMITER;
  }

  default Optional<WorkflowSpec> getWorkflowSpec() {
    return Optional.empty();
  }

  default Optional<Duration> maxReconciliationInterval() {
    return Optional.of(Duration.ofHours(MaxReconciliationInterval.DEFAULT_INTERVAL));
  }

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
    return ResourceConfiguration.super.getEffectiveNamespaces(this);
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

  <C> C getConfigurationFor(DependentResourceSpec<?, P, C> spec);
}

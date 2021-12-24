package io.javaoperatorsdk.operator.api.config;

import java.util.Collections;
import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceControllerFactory;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilters;

@SuppressWarnings("rawtypes")
public interface ControllerConfiguration<R extends HasMetadata> extends ResourceConfiguration<R> {

  default String getName() {
    return ReconcilerUtils.getDefaultReconcilerName(getAssociatedReconcilerClassName());
  }

  default String getFinalizer() {
    return ReconcilerUtils.getDefaultFinalizerName(getResourceClass());
  }

  default boolean isGenerationAware() {
    return true;
  }

  String getAssociatedReconcilerClassName();

  default RetryConfiguration getRetryConfiguration() {
    return RetryConfiguration.DEFAULT;
  }

  default boolean useFinalizer() {
    return !Constants.NO_FINALIZER
        .equals(getFinalizer());
  }

  /**
   * Allow controllers to filter events before they are provided to the
   * {@link io.javaoperatorsdk.operator.processing.event.EventHandler}. Note that the provided
   * filter is combined with {@link #isGenerationAware()} to compute the final set of filters that
   * should be applied;
   *
   * @return filter
   */
  default ResourceEventFilter<R> getEventFilter() {
    return ResourceEventFilters.passthrough();
  }

  default List<DependentResource> getDependentResources() {
    return Collections.emptyList();
  }

  default DependentResourceControllerFactory<R> dependentFactory() {
    return new DependentResourceControllerFactory<>() {};
  }
}

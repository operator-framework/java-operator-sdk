package io.javaoperatorsdk.operator.api.config;

import java.util.Collections;
import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public interface ControllerConfiguration<R extends HasMetadata> extends
    ResourceConfiguration<R, ControllerConfiguration<R>> {

  default String getName() {
    return ReconcilerUtils.getDefaultReconcilerName(getAssociatedReconcilerClassName());
  }

  default String getFinalizer() {
    return ReconcilerUtils.getDefaultFinalizerName(getResourceTypeName());
  }

  default boolean isGenerationAware() {
    return true;
  }

  String getAssociatedReconcilerClassName();

  default RetryConfiguration getRetryConfiguration() {
    return RetryConfiguration.DEFAULT;
  }

  default boolean useFinalizer() {
    return !Constants.NO_FINALIZER.equals(getFinalizer());
  }

  @Override
  default ResourceEventFilter<R, ControllerConfiguration<R>> getEventFilter() {
    return ResourceConfiguration.super.getEventFilter();
  }

  default List<? extends DependentResource> getDependentResources() {
    return Collections.emptyList();
  }
}

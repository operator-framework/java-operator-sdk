package io.javaoperatorsdk.operator.api.config;

import java.lang.reflect.ParameterizedType;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilters;

public interface ControllerConfiguration<R extends HasMetadata> {

  default String getName() {
    return ReconcilerUtils.getDefaultReconcilerName(getAssociatedReconcilerClassName());
  }

  default String getResourceTypeName() {
    return ReconcilerUtils.getResourceTypeName(getResourceClass());
  }

  default String getFinalizer() {
    return ReconcilerUtils.getDefaultFinalizerName(getResourceClass());
  }

  /**
   * Retrieves the label selector that is used to filter which custom resources are actually watched
   * by the associated controller. See
   * https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/ for more details on
   * syntax.
   *
   * @return the label selector filtering watched custom resources
   */
  default String getLabelSelector() {
    return null;
  }

  default boolean isGenerationAware() {
    return true;
  }

  default Class<R> getResourceClass() {
    ParameterizedType type = (ParameterizedType) getClass().getGenericInterfaces()[0];
    return (Class<R>) type.getActualTypeArguments()[0];
  }

  String getAssociatedReconcilerClassName();

  default String watchedNamespace() {
    return Constants.WATCH_ALL_NAMESPACE;
  }
  
  default boolean watchAllNamespaces() {
    return watchedNamespace().;
  }
  
  default boolean watchCurrentNamespace() {
    return watchedNamespace().isEmpty() ? false : watchedNamespace().equals(Constants.WATCH_CURRENT_NAMESPACE);
  }

  default RetryConfiguration getRetryConfiguration() {
    return RetryConfiguration.DEFAULT;
  }

  ConfigurationService getConfigurationService();

  default void setConfigurationService(ConfigurationService service) {}

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
}

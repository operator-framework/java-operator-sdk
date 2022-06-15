package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilters;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;

public interface ControllerConfiguration<R extends HasMetadata> extends ResourceConfiguration<R> {

  default String getName() {
    return ReconcilerUtils.getDefaultReconcilerName(getAssociatedReconcilerClassName());
  }

  default String getFinalizerName() {
    return ReconcilerUtils.getDefaultFinalizerName(getResourceClass());
  }

  default boolean isGenerationAware() {
    return true;
  }

  String getAssociatedReconcilerClassName();

  default Retry getRetry() {
    return new GenericRetry();
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
   */
  default ResourceEventFilter<R> getEventFilter() {
    return ResourceEventFilters.passthrough();
  }

  @SuppressWarnings("rawtypes")
  default List<DependentResourceSpec> getDependentResources() {
    return Collections.emptyList();
  }

  default Optional<Duration> reconciliationMaxInterval() {
    return Optional.of(Duration.ofHours(10L));
  }

  @SuppressWarnings("unused")
  default ConfigurationService getConfigurationService() {
    return ConfigurationServiceProvider.instance();
  }

  @SuppressWarnings("unchecked")
  @Override
  default Class<R> getResourceClass() {
    return (Class<R>) Utils.getFirstTypeArgumentFromSuperClassOrInterface(getClass(),
        ControllerConfiguration.class);
  }
}

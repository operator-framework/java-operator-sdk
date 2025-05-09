package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

/**
 * Implement to change a {@link io.javaoperatorsdk.operator.api.reconciler.Reconciler}'s
 * configuration at runtime
 *
 * @param <P> the primary resource type of the reconciler
 * @since 5.1
 */
public interface ConfigurableReconciler<P extends HasMetadata> {
  /**
   * Updates the reconciler's configuration by applying the modifications specified by the provided
   * {@link ControllerConfigurationOverrider} and then replacing the existing configuration in the
   * {@link ConfigurationService} for this reconciler. Note that this method will not be applied if
   * there is no configuration (as determined by {@link
   * ConfigurationService#getConfigurationFor(Reconciler)} for the reconciler.
   *
   * @param configOverrider provides the modifications to apply to the existing reconciler's
   *     configuration
   */
  void updateConfigurationFrom(ControllerConfigurationOverrider<P> configOverrider);
}

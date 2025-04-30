package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface ConfigurableReconciler<P extends HasMetadata> {
  void updateConfigurationFrom(ControllerConfigurationOverrider<P> configOverrider);
}

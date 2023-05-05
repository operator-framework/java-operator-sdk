package io.javaoperatorsdk.operator.config.runtime;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.ResolvedControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

public class DefaultConfigurationService extends BaseConfigurationService {

  @Override
  protected <R extends HasMetadata> ControllerConfiguration<R> configFor(Reconciler<R> reconciler) {
    final var other = super.configFor(reconciler);
    return new ResolvedControllerConfiguration<>(
        RuntimeControllerMetadata.getResourceClass(reconciler), other);
  }
}

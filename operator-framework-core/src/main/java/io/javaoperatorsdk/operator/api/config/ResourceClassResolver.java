package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

public interface ResourceClassResolver {

  <P extends HasMetadata> Class<P> getPrimaryResourceClass(
      Class<? extends Reconciler<P>> reconcilerClass);
}

package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

public interface ResourceClassResolver {

  <R extends HasMetadata> Class<R> getResourceClass(Class<? extends Reconciler<R>> reconcilerClass);

}

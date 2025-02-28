package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

public class DefaultResourceClassResolver implements ResourceClassResolver {

  @SuppressWarnings("unchecked")
  @Override
  public <R extends HasMetadata> Class<R> getPrimaryResourceClass(
      Class<? extends Reconciler<R>> reconcilerClass) {
    return (Class<R>)
        Utils.getFirstTypeArgumentFromSuperClassOrInterface(reconcilerClass, Reconciler.class);
  }
}

package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public abstract class GenericResourceUpdatePreProcessor<R extends HasMetadata> implements
    ResourceUpdatePreProcessor<R> {

  private GenericResourceUpdatePreProcessor() {}

  public R replaceSpecOnActual(R actual, R desired, Context<?> context) {
    var clonedActual = context.getControllerConfiguration().getConfigurationService()
        .getResourceCloner()
        .clone(actual);
    updateClonedActual(clonedActual, desired);
    return clonedActual;
  }

  protected abstract void updateClonedActual(R actual, R desired);
}

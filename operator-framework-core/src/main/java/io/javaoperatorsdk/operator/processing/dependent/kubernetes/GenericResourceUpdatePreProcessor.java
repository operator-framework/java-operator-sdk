package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public abstract class GenericResourceUpdatePreProcessor<R extends HasMetadata> implements
    ResourceUpdatePreProcessor<R> {

  private GenericResourceUpdatePreProcessor() {}

  @SuppressWarnings("unchecked")
  public static <R extends HasMetadata> ResourceUpdatePreProcessor<R> processorFor(
      Class<R> resourceType) {
    if (Secret.class.isAssignableFrom(resourceType)) {
      return (ResourceUpdatePreProcessor<R>) new GenericResourceUpdatePreProcessor<Secret>() {
        @Override
        protected void updateClonedActual(Secret actual, Secret desired) {
          actual.setData(desired.getData());
          actual.setStringData(desired.getStringData());
        }
      };
    } else if (ConfigMap.class.isAssignableFrom(resourceType)) {
      return (ResourceUpdatePreProcessor<R>) new GenericResourceUpdatePreProcessor<ConfigMap>() {

        @Override
        protected void updateClonedActual(ConfigMap actual, ConfigMap desired) {
          actual.setData(desired.getData());
          actual.setBinaryData((desired.getBinaryData()));
        }
      };
    } else {
      return new GenericResourceUpdatePreProcessor<>() {
        @Override
        protected void updateClonedActual(R actual, R desired) {
          var desiredSpec = ReconcilerUtils.getSpec(desired);
          ReconcilerUtils.setSpec(actual, desiredSpec);
        }
      };
    }
  }

  public R replaceSpecOnActual(R actual, R desired, Context<?> context) {
    var clonedActual = ConfigurationServiceProvider.instance().getResourceCloner().clone(actual);
    updateClonedActual(clonedActual, desired);
    return clonedActual;
  }

  protected abstract void updateClonedActual(R actual, R desired);
}

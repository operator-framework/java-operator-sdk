package io.javaoperatorsdk.operator.processing.dependent.kubernetes.processors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.ResourceUpdatePreProcessor;

public class GenericResourceUpdatePreProcessor<R extends HasMetadata> implements
    ResourceUpdatePreProcessor<R> {
  private static final ResourceUpdatePreProcessor<?> INSTANCE =
      new GenericResourceUpdatePreProcessor<>();

  protected GenericResourceUpdatePreProcessor() {}

  @SuppressWarnings("unchecked")
  public static <R extends HasMetadata> ResourceUpdatePreProcessor<R> processorFor(
      Class<R> resourceType) {
    if (Secret.class.isAssignableFrom(resourceType)) {
      return (ResourceUpdatePreProcessor<R>) SecretResourceUpdatePreProcessor.INSTANCE;
    } else if (ConfigMap.class.isAssignableFrom(resourceType)) {
      return (ResourceUpdatePreProcessor<R>) ConfigMapResourceUpdatePreProcessor.INSTANCE;
    } else {
      return (ResourceUpdatePreProcessor<R>) INSTANCE;
    }
  }

  public R replaceSpecOnActual(R actual, R desired, Context<?> context) {
    var clonedActual = ConfigurationServiceProvider.instance().getResourceCloner().clone(actual);
    updateClonedActual(clonedActual, desired);
    return clonedActual;
  }

  protected void updateClonedActual(R actual, R desired) {
    var desiredSpec = ReconcilerUtils.getSpec(desired);
    ReconcilerUtils.setSpec(actual, desiredSpec);
  }
}

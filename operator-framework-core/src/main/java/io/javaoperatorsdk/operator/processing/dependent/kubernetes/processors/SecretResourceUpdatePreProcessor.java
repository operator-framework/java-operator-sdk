package io.javaoperatorsdk.operator.processing.dependent.kubernetes.processors;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.ResourceUpdatePreProcessor;

public class SecretResourceUpdatePreProcessor extends GenericResourceUpdatePreProcessor<Secret> {
  final static ResourceUpdatePreProcessor<Secret> INSTANCE = new SecretResourceUpdatePreProcessor();

  @Override
  protected void updateClonedActual(Secret actual, Secret desired) {
    actual.setData(desired.getData());
    actual.setStringData(desired.getStringData());
  }
}

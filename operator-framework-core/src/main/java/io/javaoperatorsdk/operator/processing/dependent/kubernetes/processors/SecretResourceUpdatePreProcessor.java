package io.javaoperatorsdk.operator.processing.dependent.kubernetes.processors;

import io.fabric8.kubernetes.api.model.Secret;

public class SecretResourceUpdatePreProcessor extends GenericResourceUpdatePreProcessor<Secret> {

  @Override
  protected void updateClonedActual(Secret actual, Secret desired) {
    actual.setData(desired.getData());
    actual.setStringData(desired.getStringData());
    actual.setImmutable(desired.getImmutable());
    actual.setType(desired.getType());
  }
}

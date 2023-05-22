package io.javaoperatorsdk.operator.processing.dependent.kubernetes.processors;

import io.fabric8.kubernetes.api.model.ConfigMap;

public class ConfigMapResourceUpdatePreProcessor
    extends GenericResourceUpdatePreProcessor<ConfigMap> {

  @Override
  protected void updateClonedActual(ConfigMap actual, ConfigMap desired) {
    actual.setData(desired.getData());
    actual.setBinaryData((desired.getBinaryData()));
    actual.setImmutable(desired.getImmutable());
  }
}

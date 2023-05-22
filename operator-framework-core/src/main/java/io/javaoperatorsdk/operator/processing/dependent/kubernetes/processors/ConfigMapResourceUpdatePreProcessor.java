package io.javaoperatorsdk.operator.processing.dependent.kubernetes.processors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.ResourceUpdatePreProcessor;

public class ConfigMapResourceUpdatePreProcessor
    extends GenericResourceUpdatePreProcessor<ConfigMap> {
  final static ResourceUpdatePreProcessor<ConfigMap> INSTANCE =
      new ConfigMapResourceUpdatePreProcessor();

  @Override
  protected void updateClonedActual(ConfigMap actual, ConfigMap desired) {
    actual.setData(desired.getData());
    actual.setBinaryData((desired.getBinaryData()));
  }
}

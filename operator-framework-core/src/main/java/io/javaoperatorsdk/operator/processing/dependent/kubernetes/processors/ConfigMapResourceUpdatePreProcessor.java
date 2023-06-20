package io.javaoperatorsdk.operator.processing.dependent.kubernetes.processors;

import java.util.Objects;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public class ConfigMapResourceUpdatePreProcessor
    extends GenericResourceUpdatePreProcessor<ConfigMap> {

  @Override
  protected void updateClonedActual(ConfigMap actual, ConfigMap desired) {
    actual.setData(desired.getData());
    actual.setBinaryData((desired.getBinaryData()));
    actual.setImmutable(desired.getImmutable());
  }

  @Override
  public boolean matches(ConfigMap actual, ConfigMap desired, boolean equality,
      Context<?> context, String[] ignoredPaths) {
    return Objects.equals(actual.getImmutable(), desired.getImmutable()) &&
        Objects.equals(actual.getData(), desired.getData()) &&
        Objects.equals(actual.getBinaryData(), desired.getBinaryData());
  }
}

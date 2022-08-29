package io.javaoperatorsdk.operator.sample.multipledependentresource;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class ConfigMapFilter {
  private final ResourceID targetResourceID;

  public ConfigMapFilter(MultipleDependentResourceCustomResource primary, int suffix) {
    final var metadata = primary.getMetadata();
    targetResourceID = new ResourceID(primary.getConfigMapName(suffix), metadata.getNamespace());
  }

  public boolean matches(ConfigMap configMap) {
    return targetResourceID.isSameResource(configMap);
  }
}

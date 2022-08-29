package io.javaoperatorsdk.operator.sample.orderedmanageddependent;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class ConfigMapFilter {
  private final ResourceID targetResourceID;

  public ConfigMapFilter(HasMetadata primary, String suffix) {
    final var metadata = primary.getMetadata();
    targetResourceID = new ResourceID(metadata.getName() + suffix, metadata.getNamespace());
  }

  public boolean matches(ConfigMap configMap) {
    return targetResourceID.isSameResource(configMap);
  }
}

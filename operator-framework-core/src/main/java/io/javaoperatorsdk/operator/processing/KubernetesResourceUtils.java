package io.javaoperatorsdk.operator.processing;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class KubernetesResourceUtils {

  public static String getUID(HasMetadata customResource) {
    return customResource.getMetadata().getUid();
  }

  public static String getVersion(HasMetadata customResource) {
    return customResource.getMetadata().getResourceVersion();
  }
}

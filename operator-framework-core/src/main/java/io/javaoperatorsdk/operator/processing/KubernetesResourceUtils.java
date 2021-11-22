package io.javaoperatorsdk.operator.processing;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class KubernetesResourceUtils {

  public static String getName(HasMetadata resource) {
    return resource.getMetadata().getName();
  }

  public static String getUID(HasMetadata resource) {
    return resource.getMetadata().getUid();
  }

  public static String getVersion(HasMetadata resource) {
    return resource.getMetadata().getResourceVersion();
  }
}

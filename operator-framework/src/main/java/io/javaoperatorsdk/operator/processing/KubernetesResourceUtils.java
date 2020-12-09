package io.javaoperatorsdk.operator.processing;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.CustomResource;

public class KubernetesResourceUtils {

  public static String getUID(HasMetadata customResource) {
    return customResource.getMetadata().getUid();
  }

  public static String getVersion(HasMetadata customResource) {
    return customResource.getMetadata().getResourceVersion();
  }

  public static boolean markedForDeletion(CustomResource resource) {
    return resource.getMetadata().getDeletionTimestamp() != null
        && !resource.getMetadata().getDeletionTimestamp().isEmpty();
  }
}

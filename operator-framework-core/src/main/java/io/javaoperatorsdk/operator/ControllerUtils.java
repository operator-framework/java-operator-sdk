package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.client.CustomResource;

public class ControllerUtils {

  private static final String FINALIZER_NAME_SUFFIX = "/finalizer";

  public static String getDefaultFinalizerName(String crdName) {
    return crdName + FINALIZER_NAME_SUFFIX;
  }

  public static boolean hasGivenFinalizer(CustomResource resource, String finalizer) {
    return resource.getMetadata().getFinalizers() != null
        && resource.getMetadata().getFinalizers().contains(finalizer);
  }
}

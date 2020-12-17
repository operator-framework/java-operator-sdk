package io.javaoperatorsdk.operator.api;

import io.fabric8.kubernetes.client.CustomResource;
import java.util.Locale;

public class ControllerUtils {

  private static final String FINALIZER_NAME_SUFFIX = "/finalizer";

  public static String getDefaultFinalizerName(String crdName) {
    return crdName + FINALIZER_NAME_SUFFIX;
  }

  public static boolean hasGivenFinalizer(CustomResource resource, String finalizer) {
    return resource.getMetadata().getFinalizers() != null
        && resource.getMetadata().getFinalizers().contains(finalizer);
  }

  public static String getDefaultNameFor(ResourceController controller) {
    return getDefaultNameFor(controller.getClass());
  }

  public static String getDefaultNameFor(Class<? extends ResourceController> controllerClass) {
    return getDefaultResourceControllerName(controllerClass.getSimpleName());
  }

  public static String getDefaultResourceControllerName(String rcControllerClassName) {
    final var lastDot = rcControllerClassName.lastIndexOf('.');
    if (lastDot > 0) {
      rcControllerClassName = rcControllerClassName.substring(lastDot);
    }
    return rcControllerClassName.toLowerCase(Locale.ROOT);
  }
}

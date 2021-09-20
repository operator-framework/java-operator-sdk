package io.javaoperatorsdk.operator;

import java.util.Locale;

import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;

public class ControllerUtils {

  private static final String FINALIZER_NAME_SUFFIX = "/finalizer";

  public static String getDefaultFinalizerName(String crdName) {
    return crdName + FINALIZER_NAME_SUFFIX;
  }

  public static String getNameFor(Class<? extends ResourceController> controllerClass) {
    // if the controller annotation has a name attribute, use it
    final var annotation = controllerClass.getAnnotation(Controller.class);
    if (annotation != null) {
      final var name = annotation.name();
      if (!Controller.NULL.equals(name)) {
        return name;
      }
    }

    // otherwise, use the lower-cased full class name
    return getDefaultNameFor(controllerClass);
  }

  public static String getNameFor(ResourceController controller) {
    return getNameFor(controller.getClass());
  }

  public static String getDefaultNameFor(ResourceController controller) {
    return getDefaultNameFor(controller.getClass());
  }

  public static String getDefaultNameFor(Class<? extends ResourceController> controllerClass) {
    return getDefaultResourceControllerName(controllerClass.getSimpleName());
  }

  public static String getDefaultResourceControllerName(String rcControllerClassName) {
    // if the name is fully qualified, extract the simple class name
    final var lastDot = rcControllerClassName.lastIndexOf('.');
    if (lastDot > 0) {
      rcControllerClassName = rcControllerClassName.substring(lastDot + 1);
    }
    return rcControllerClassName.toLowerCase(Locale.ROOT);
  }
}

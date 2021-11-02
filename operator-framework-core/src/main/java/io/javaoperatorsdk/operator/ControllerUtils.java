package io.javaoperatorsdk.operator;

import java.util.Locale;

import io.javaoperatorsdk.operator.api.reconciler.Controller;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

@SuppressWarnings("rawtypes")
public class ControllerUtils {

  private static final String FINALIZER_NAME_SUFFIX = "/finalizer";

  public static String getDefaultFinalizerName(String crdName) {
    return crdName + FINALIZER_NAME_SUFFIX;
  }

  public static String getNameFor(Class<? extends Reconciler> controllerClass) {
    // if the controller annotation has a name attribute, use it
    final var annotation = controllerClass.getAnnotation(Controller.class);
    if (annotation != null) {
      final var name = annotation.name();
      if (!Controller.EMPTY_STRING.equals(name)) {
        return name;
      }
    }

    // otherwise, use the lower-cased full class name
    return getDefaultNameFor(controllerClass);
  }

  public static String getNameFor(Reconciler controller) {
    return getNameFor(controller.getClass());
  }

  public static String getDefaultNameFor(Reconciler controller) {
    return getDefaultNameFor(controller.getClass());
  }

  public static String getDefaultNameFor(Class<? extends Reconciler> reconcilerClass) {
    return getDefaultResourceReconcilerName(reconcilerClass.getSimpleName());
  }

  public static String getDefaultResourceReconcilerName(String rcControllerClassName) {
    // if the name is fully qualified, extract the simple class name
    final var lastDot = rcControllerClassName.lastIndexOf('.');
    if (lastDot > 0) {
      rcControllerClassName = rcControllerClassName.substring(lastDot + 1);
    }
    return rcControllerClassName.toLowerCase(Locale.ROOT);
  }
}

package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import java.util.Map;
import org.apache.commons.lang3.ClassUtils;

public class ControllerUtils {

  private static final String FINALIZER_NAME_SUFFIX = "/finalizer";
  public static final String CONTROLLERS_RESOURCE_PATH = "javaoperatorsdk/controllers";
  private static Map<Class<? extends ResourceController>, Class<? extends CustomResource>>
      controllerToCustomResourceMappings;

  static {
    controllerToCustomResourceMappings =
        ControllerToCustomResourceMappingsProvider.provide(CONTROLLERS_RESOURCE_PATH);
  }

  static String getFinalizer(ResourceController controller) {
    final String annotationFinalizerName = getAnnotation(controller).finalizerName();
    if (!Controller.NULL.equals(annotationFinalizerName)) {
      return annotationFinalizerName;
    }
    return getAnnotation(controller).crdName() + FINALIZER_NAME_SUFFIX;
  }

  static boolean getGenerationEventProcessing(ResourceController<?> controller) {
    return getAnnotation(controller).generationAwareEventProcessing();
  }

  static <R extends CustomResource> Class<R> getCustomResourceClass(
      ResourceController<R> controller) {
    final Class<? extends CustomResource> customResourceClass =
        controllerToCustomResourceMappings.get(controller.getClass());
    if (customResourceClass == null) {
      throw new IllegalArgumentException(
          String.format(
              "No custom resource has been found for controller %s",
              controller.getClass().getCanonicalName()));
    }
    return (Class<R>) customResourceClass;
  }

  static String getCrdName(ResourceController controller) {
    return getAnnotation(controller).crdName();
  }

  public static <T extends CustomResource>
      Class<? extends CustomResourceDoneable<T>> getCustomResourceDoneableClass(
          ResourceController<T> controller) {
    try {
      final Class<T> customResourceClass = getCustomResourceClass(controller);
      return (Class<? extends CustomResourceDoneable<T>>)
          ClassUtils.getClass(customResourceClass.getCanonicalName() + "Doneable");
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      return null;
    }
  }

  private static Controller getAnnotation(ResourceController<?> controller) {
    return controller.getClass().getAnnotation(Controller.class);
  }

  public static boolean hasGivenFinalizer(CustomResource resource, String finalizer) {
    return resource.getMetadata().getFinalizers() != null
        && resource.getMetadata().getFinalizers().contains(finalizer);
  }
}

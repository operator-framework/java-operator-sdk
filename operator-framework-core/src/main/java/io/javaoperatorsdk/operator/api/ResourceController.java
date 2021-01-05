package io.javaoperatorsdk.operator.api;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import java.util.Locale;

public interface ResourceController<R extends CustomResource> {

  /**
   * The implementation should delete the associated component(s). Note that this is method is
   * called when an object is marked for deletion. After its executed the custom resource finalizer
   * is automatically removed by the framework; unless the return value is false - note that this is
   * almost never the case. Its important to have the implementation also idempotent, in the current
   * implementation to cover all edge cases actually will be executed mostly twice.
   *
   * @param resource
   * @return true - so the finalizer is automatically removed after the call. false if you don't
   *     want to remove the finalizer. Note that this is ALMOST NEVER the case.
   */
  DeleteControl deleteResource(R resource, Context<R> context);

  /**
   * The implementation of this operation is required to be idempotent. Always use the UpdateControl
   * object to make updates on custom resource if possible. Also always use the custom resource
   * parameter (not the custom resource that might be in the events)
   *
   * @return The resource is updated in api server if the return value is present within Optional.
   *     This the common use cases. However in cases, for example the operator is restarted, and we
   *     don't want to have an update call to k8s api to be made unnecessarily, by returning an
   *     empty Optional this update can be skipped. <b>However we will always call an update if
   *     there is no finalizer on object and its not marked for deletion.</b>
   */
  UpdateControl<R> createOrUpdateResource(R resource, Context<R> context);

  /**
   * In init typically you might want to register event sources.
   *
   * @param eventSourceManager
   */
  default void init(EventSourceManager eventSourceManager) {}

  default String getName() {
    return getNameFor(this);
  }

  static String getNameFor(Class<? extends ResourceController> controllerClass) {
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

  static String getNameFor(ResourceController controller) {
    return getNameFor(controller.getClass());
  }

  static String getDefaultNameFor(ResourceController controller) {
    return getDefaultNameFor(controller.getClass());
  }

  static String getDefaultNameFor(Class<? extends ResourceController> controllerClass) {
    return getDefaultResourceControllerName(controllerClass.getSimpleName());
  }

  static String getDefaultResourceControllerName(String rcControllerClassName) {
    // if the name is fully qualified, extract the simple class name
    final var lastDot = rcControllerClassName.lastIndexOf('.');
    if (lastDot > 0) {
      rcControllerClassName = rcControllerClassName.substring(lastDot);
    }
    return rcControllerClassName.toLowerCase(Locale.ROOT);
  }
}

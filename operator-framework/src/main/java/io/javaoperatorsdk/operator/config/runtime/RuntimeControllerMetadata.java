package io.javaoperatorsdk.operator.config.runtime;

import java.util.Map;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.ResourceController;

public class RuntimeControllerMetadata {

  public static final String CONTROLLERS_RESOURCE_PATH = "javaoperatorsdk/controllers";
  public static final String DONEABLES_RESOURCE_PATH = "javaoperatorsdk/doneables";
  private static final Map<Class<? extends ResourceController>, Class<? extends CustomResource>> controllerToCustomResourceMappings;

  static {
    controllerToCustomResourceMappings =
        ClassMappingProvider.provide(
            CONTROLLERS_RESOURCE_PATH, ResourceController.class, CustomResource.class);
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
}

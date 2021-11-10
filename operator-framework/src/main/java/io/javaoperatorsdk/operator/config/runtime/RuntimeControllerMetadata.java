package io.javaoperatorsdk.operator.config.runtime;

import java.util.Map;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

@SuppressWarnings("rawtypes")
public class RuntimeControllerMetadata {

  public static final String RECONCILERS_RESOURCE_PATH = "javaoperatorsdk/reconcilers";
  private static final Map<Class<? extends Reconciler>, Class<? extends CustomResource>> controllerToCustomResourceMappings;

  static {
    controllerToCustomResourceMappings =
        ClassMappingProvider.provide(
            RECONCILERS_RESOURCE_PATH, Reconciler.class, CustomResource.class);
  }

  static <R extends CustomResource<?, ?>> Class<R> getCustomResourceClass(
      Reconciler<R> reconciler) {
    final Class<? extends CustomResource> customResourceClass =
        controllerToCustomResourceMappings.get(reconciler.getClass());
    if (customResourceClass == null) {
      throw new IllegalArgumentException(
          String.format(
              "No custom resource has been found for controller %s",
              reconciler.getClass().getCanonicalName()));
    }
    return (Class<R>) customResourceClass;
  }
}

package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.Controller;
import java.lang.reflect.ParameterizedType;
import java.util.Collections;
import java.util.Set;

public interface ControllerConfiguration<R extends CustomResource> {

  default String getName() {
    return ControllerUtils.getDefaultResourceControllerName(getAssociatedControllerClassName());
  }

  default String getCRDName() {
    return CustomResource.getCRDName(getCustomResourceClass());
  }

  default String getFinalizer() {
    return ControllerUtils.getDefaultFinalizerName(getCRDName());
  }

  default String getLabelSelector() {
    return null;
  }

  default boolean isGenerationAware() {
    return true;
  }

  default Class<R> getCustomResourceClass() {
    ParameterizedType type = (ParameterizedType) getClass().getGenericInterfaces()[0];
    return (Class<R>) type.getActualTypeArguments()[0];
  }

  String getAssociatedControllerClassName();

  default Set<String> getNamespaces() {
    return Collections.emptySet();
  }

  default boolean watchAllNamespaces() {
    return allNamespacesWatched(getNamespaces());
  }

  static boolean allNamespacesWatched(Set<String> namespaces) {
    return namespaces == null || namespaces.isEmpty();
  }

  default boolean watchCurrentNamespace() {
    return currentNamespaceWatched(getNamespaces());
  }

  static boolean currentNamespaceWatched(Set<String> namespaces) {
    return namespaces != null
        && namespaces.size() == 1
        && namespaces.contains(Controller.WATCH_CURRENT_NAMESPACE);
  }

  /**
   * Computes the effective namespaces based on the set specified by the user, in particular
   * retrieves the current namespace from the client when the user specified that they wanted to
   * watch the current namespace only.
   *
   * @return a Set of namespace names the associated controller will watch
   */
  default Set<String> getEffectiveNamespaces() {
    var targetNamespaces = getNamespaces();
    if (watchCurrentNamespace()) {
      final var parent = getConfigurationService();
      if (parent == null) {
        throw new IllegalStateException(
            "Parent ConfigurationService must be set before calling this method");
      }
      targetNamespaces = Collections.singleton(parent.getClientConfiguration().getNamespace());
    }
    return targetNamespaces;
  }

  default RetryConfiguration getRetryConfiguration() {
    return RetryConfiguration.DEFAULT;
  }

  ConfigurationService getConfigurationService();

  default void setConfigurationService(ConfigurationService service) {}

  default boolean useFinalizer() {
    return !Controller.NO_FINALIZER.equals(getFinalizer());
  }
}

package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.Controller;
import java.util.Collections;
import java.util.Set;

public interface ControllerConfiguration<R extends CustomResource> {

  String getName();

  String getCRDName();

  String getFinalizer();

  boolean isGenerationAware();

  Class<R> getCustomResourceClass();

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

  void setConfigurationService(ConfigurationService service);

  default boolean useFinalizer() {
    return !Controller.NO_FINALIZER.equals(getFinalizer());
  }
}

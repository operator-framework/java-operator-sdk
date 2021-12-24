package io.javaoperatorsdk.operator.api.config;

import java.util.Collections;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Constants;

public interface ResourceConfiguration<R extends HasMetadata> {

  default String getResourceTypeName() {
    return ReconcilerUtils.getResourceTypeName(getResourceClass());
  }

  /**
   * Retrieves the label selector that is used to filter which resources are actually watched by the
   * associated event source. See
   * https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/ for more details on
   * syntax.
   *
   * @return the label selector filtering watched resources
   */
  default String getLabelSelector() {
    return null;
  }

  @SuppressWarnings("unchecked")
  default Class<R> getResourceClass() {
    return (Class<R>) Utils.getFirstTypeArgumentFromInterface(getClass());
  }

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
        && namespaces.contains(Constants.WATCH_CURRENT_NAMESPACE);
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

  ConfigurationService getConfigurationService();

  void setConfigurationService(ConfigurationService service);
}

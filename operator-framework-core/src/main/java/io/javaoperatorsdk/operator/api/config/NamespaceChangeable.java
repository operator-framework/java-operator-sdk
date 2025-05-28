package io.javaoperatorsdk.operator.api.config;

import java.util.Set;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;

public interface NamespaceChangeable {

  /**
   * If the controller and possibly registered {@link
   * io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource} watches a set
   * of namespaces this set can be adjusted dynamically, this when the operator is running.
   *
   * @param namespaces target namespaces to watch
   */
  void changeNamespaces(Set<String> namespaces);

  @SuppressWarnings("unused")
  default void changeNamespaces(String... namespaces) {
    changeNamespaces(namespaces != null ? Set.of(namespaces) : DEFAULT_NAMESPACES_SET);
  }

  default boolean allowsNamespaceChanges() {
    return true;
  }
}

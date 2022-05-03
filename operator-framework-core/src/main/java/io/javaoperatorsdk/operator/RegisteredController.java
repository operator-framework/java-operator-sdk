package io.javaoperatorsdk.operator;

import java.util.Set;

public interface RegisteredController {

  /**
   * If the controller and possibly registered
   * {@link io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource}
   * watches a set of namespaces this set can be adjusted dynamically, this when the operator is
   * running.
   *
   * @param namespaces target namespaces to watch
   */
  void changeNamespaces(Set<String> namespaces);

  void changeNamespaces(String... namespaces);

}

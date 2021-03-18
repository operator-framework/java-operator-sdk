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
    return getNamespaces().isEmpty();
  }

  default boolean watchCurrentNamespace() {
    final var namespaces = getNamespaces();
    return namespaces.size() == 1 && namespaces.contains(Controller.WATCH_CURRENT_NAMESPACE);
  }

  default RetryConfiguration getRetryConfiguration() {
    return RetryConfiguration.DEFAULT;
  }
}

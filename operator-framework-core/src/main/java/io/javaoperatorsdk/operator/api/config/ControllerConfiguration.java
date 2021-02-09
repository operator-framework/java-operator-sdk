package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.client.CustomResource;
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

  default RetryConfiguration getRetryConfiguration() {
    return RetryConfiguration.DEFAULT;
  }
}

package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import java.util.Collections;
import java.util.Set;

public interface ControllerConfiguration<R extends CustomResource> {
  String WATCH_ALL_NAMESPACES_MARKER = "ALL_NAMESPACES";

  String getName();

  String getCRDName();

  String getFinalizer();

  boolean isGenerationAware();

  Class<R> getCustomResourceClass();

  Class<? extends CustomResourceDoneable<R>> getDoneableClass();

  default boolean isClusterScoped() {
    return false;
  }

  default Set<String> getNamespaces() {
    return Collections.emptySet();
  }

  default boolean watchAllNamespaces() {
    return getNamespaces().contains(WATCH_ALL_NAMESPACES_MARKER);
  }

  default RetryConfiguration getRetryConfiguration() {
    return RetryConfiguration.DEFAULT;
  }
}

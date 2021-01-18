package io.javaoperatorsdk.quarkus.extension;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.quarkus.runtime.annotations.RecordableConstructor;
import java.util.Collections;
import java.util.Set;

public class QuarkusControllerConfiguration<R extends CustomResource>
    implements ControllerConfiguration<R> {
  private final String associatedControllerClassName;
  private final String name;
  private final String crdName;
  private final String finalizer;
  private final boolean generationAware;
  private final Set<String> namespaces;
  private final String crClass;
  private final boolean watchAllNamespaces;
  private final RetryConfiguration retryConfiguration;

  @RecordableConstructor
  public QuarkusControllerConfiguration(
      String associatedControllerClassName,
      String name,
      String crdName,
      String finalizer,
      boolean generationAware,
      Set<String> namespaces,
      String crClass,
      RetryConfiguration retryConfiguration) {
    this.associatedControllerClassName = associatedControllerClassName;
    this.name = name;
    this.crdName = crdName;
    this.finalizer = finalizer;
    this.generationAware = generationAware;
    this.namespaces = namespaces;
    this.crClass = crClass;
    this.watchAllNamespaces = this.namespaces.contains(WATCH_ALL_NAMESPACES_MARKER);
    this.retryConfiguration =
        retryConfiguration == null
            ? ControllerConfiguration.super.getRetryConfiguration()
            : retryConfiguration;
  }

  public static Set<String> asSet(String[] namespaces) {
    return namespaces == null || namespaces.length == 0
        ? Collections.emptySet()
        : Set.of(namespaces);
  }

  // Needed for Quarkus to find the associated constructor parameter
  public String getCrdName() {
    return getCRDName();
  }

  // Needed for Quarkus to find the associated constructor parameter
  public String getCrClass() {
    return crClass;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getCRDName() {
    return crdName;
  }

  @Override
  public String getFinalizer() {
    return finalizer;
  }

  @Override
  public boolean isGenerationAware() {
    return generationAware;
  }

  @Override
  public Class<R> getCustomResourceClass() {
    return (Class<R>) loadClass(crClass);
  }

  private Class<?> loadClass(String className) {
    try {
      return Thread.currentThread().getContextClassLoader().loadClass(className);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException("Couldn't find class " + className);
    }
  }

  @Override
  public String getAssociatedControllerClassName() {
    return associatedControllerClassName;
  }

  @Override
  public Set<String> getNamespaces() {
    return namespaces;
  }

  @Override
  public boolean watchAllNamespaces() {
    return watchAllNamespaces;
  }

  @Override
  public RetryConfiguration getRetryConfiguration() {
    return retryConfiguration;
  }
}

package io.javaoperatorsdk.quarkus.extension;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.config.AbstractControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.quarkus.runtime.annotations.RecordableConstructor;
import java.util.Collections;
import java.util.Set;

public class QuarkusControllerConfiguration<R extends CustomResource>
    extends AbstractControllerConfiguration<R> {

  private final String crClass;
  private Class<R> clazz;

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
    super(
        associatedControllerClassName,
        name,
        crdName,
        finalizer,
        generationAware,
        namespaces,
        retryConfiguration);
    this.crClass = crClass;
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
  public Class<R> getCustomResourceClass() {
    if (clazz == null) {
      clazz = (Class<R>) loadClass(crClass);
    }
    return clazz;
  }

  private Class<?> loadClass(String className) {
    try {
      return Thread.currentThread().getContextClassLoader().loadClass(className);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException("Couldn't find class " + className);
    }
  }
}

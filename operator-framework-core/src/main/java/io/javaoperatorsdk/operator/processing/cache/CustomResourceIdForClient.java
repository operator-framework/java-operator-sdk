package io.javaoperatorsdk.operator.processing.cache;

import java.util.Objects;

// todo better naming

/** Since we need to */
class CustomResourceIdForClient {

  private final String namespace;
  private final String name;

  CustomResourceIdForClient(String namespace, String name) {
    this.namespace = namespace;
    this.name = name;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getName() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CustomResourceIdForClient that = (CustomResourceIdForClient) o;
    return Objects.equals(namespace, that.namespace) && Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(namespace, name);
  }
}

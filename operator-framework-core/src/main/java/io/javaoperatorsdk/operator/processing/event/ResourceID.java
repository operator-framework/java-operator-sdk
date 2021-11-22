package io.javaoperatorsdk.operator.processing.event;

import java.util.Objects;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class ResourceID {

  public static ResourceID fromResource(HasMetadata resource) {
    return new ResourceID(resource.getMetadata().getName(),
        resource.getMetadata().getNamespace());
  }

  private final String name;
  private final String namespace;

  public ResourceID(String name, String namespace) {
    this.name = name;
    this.namespace = namespace;
  }

  public ResourceID(String name) {
    this(name, null);
  }

  public String getName() {
    return name;
  }

  public Optional<String> getNamespace() {
    return Optional.ofNullable(namespace);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    ResourceID that = (ResourceID) o;
    return Objects.equals(name, that.name) && Objects.equals(namespace,
        that.namespace);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, namespace);
  }

  @Override
  public String toString() {
    return "CustomResourceID{" +
        "name='" + name + '\'' +
        ", namespace='" + namespace + '\'' +
        '}';
  }
}

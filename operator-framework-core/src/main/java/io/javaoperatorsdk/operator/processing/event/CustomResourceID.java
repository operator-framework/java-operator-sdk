package io.javaoperatorsdk.operator.processing.event;

import java.util.Objects;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class CustomResourceID {

  public static CustomResourceID fromResource(HasMetadata resource) {
    return new CustomResourceID(resource.getMetadata().getName(),
        resource.getMetadata().getNamespace());
  }

  private String name;
  private String namespace;

  public CustomResourceID(String name, String namespace) {
    this.name = name;
    this.namespace = namespace;
  }

  public CustomResourceID(String name) {
    this.name = name;
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
    CustomResourceID that = (CustomResourceID) o;
    return Objects.equals(name, that.name) && Objects.equals(namespace,
        that.namespace);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, namespace);
  }
}

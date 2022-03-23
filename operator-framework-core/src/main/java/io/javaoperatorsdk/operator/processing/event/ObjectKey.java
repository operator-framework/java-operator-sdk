package io.javaoperatorsdk.operator.processing.event;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class ObjectKey implements Serializable {

  public static ObjectKey fromResource(HasMetadata resource) {
    return new ObjectKey(resource.getMetadata().getName(),
        resource.getMetadata().getNamespace());
  }

  public static Optional<ObjectKey> fromFirstOwnerReference(HasMetadata resource) {
    var ownerReferences = resource.getMetadata().getOwnerReferences();
    if (!ownerReferences.isEmpty()) {
      return Optional.of(new ObjectKey(ownerReferences.get(0).getName(),
          resource.getMetadata().getNamespace()));
    } else {
      return Optional.empty();
    }
  }

  private final String name;
  private final String namespace;

  public ObjectKey(String name, String namespace) {
    this.name = name;
    this.namespace = namespace;
  }

  public ObjectKey(String name) {
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
    ObjectKey that = (ObjectKey) o;
    return Objects.equals(name, that.name) && Objects.equals(namespace,
        that.namespace);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, namespace);
  }

  @Override
  public String toString() {
    return "ObjectKey{" +
        "name='" + name + '\'' +
        ", namespace='" + namespace + '\'' +
        '}';
  }
}

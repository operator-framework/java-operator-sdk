package io.javaoperatorsdk.operator.processing.event;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;

public class ResourceID implements Serializable {

  public static ResourceID fromResource(HasMetadata resource) {
    return new ResourceID(resource.getMetadata().getName(), resource.getMetadata().getNamespace());
  }

  public static ResourceID fromOwnerReference(
      HasMetadata resource, OwnerReference ownerReference, boolean clusterScoped) {
    return new ResourceID(
        ownerReference.getName(), clusterScoped ? null : resource.getMetadata().getNamespace());
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
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ResourceID that = (ResourceID) o;
    return Objects.equals(name, that.name) && Objects.equals(namespace, that.namespace);
  }

  public boolean isSameResource(HasMetadata hasMetadata) {
    final var metadata = hasMetadata.getMetadata();
    return getName().equals(metadata.getName())
        && getNamespace().map(ns -> ns.equals(metadata.getNamespace())).orElse(true);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, namespace);
  }

  @Override
  public String toString() {
    return toString(name, namespace);
  }

  public static String toString(HasMetadata resource) {
    return toString(resource.getMetadata().getName(), resource.getMetadata().getNamespace());
  }

  private static String toString(String name, String namespace) {
    return "ResourceID{" + "name='" + name + '\'' + ", namespace='" + namespace + '\'' + '}';
  }
}

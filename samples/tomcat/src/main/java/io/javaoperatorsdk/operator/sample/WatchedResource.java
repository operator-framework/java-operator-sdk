package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.HasMetadata;
import java.util.Objects;
import org.apache.commons.lang3.builder.EqualsBuilder;

class WatchedResource {
  private final String type;
  private final String name;

  public WatchedResource(String type, String name) {
    this.type = type;
    this.name = name;
  }

  public static WatchedResource fromResource(HasMetadata resource) {
    return new WatchedResource(resource.getKind(), resource.getMetadata().getName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    WatchedResource that = (WatchedResource) o;

    return new EqualsBuilder().append(type, that.type).append(name, that.name).isEquals();
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, name);
  }

  @Override
  public String toString() {
    return "WatchedResource{" + "type='" + type + '\'' + ", name='" + name + '\'' + '}';
  }
}

package io.javaoperatorsdk.operator.dependent.bulkdependent.external;

import java.util.Objects;

public class ExternalResource {

  private final String id;
  private final String data;

  public ExternalResource(String id, String data) {
    this.id = id;
    this.data = data;
  }

  public String getId() {
    return id;
  }

  public String getData() {
    return data;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ExternalResource that = (ExternalResource) o;
    return Objects.equals(id, that.id) && Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, data);
  }
}

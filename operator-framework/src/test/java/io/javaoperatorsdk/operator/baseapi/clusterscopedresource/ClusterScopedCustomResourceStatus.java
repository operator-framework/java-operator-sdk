package io.javaoperatorsdk.operator.baseapi.clusterscopedresource;

public class ClusterScopedCustomResourceStatus {

  private Boolean created;

  public Boolean getCreated() {
    return created;
  }

  public ClusterScopedCustomResourceStatus setCreated(Boolean created) {
    this.created = created;
    return this;
  }
}

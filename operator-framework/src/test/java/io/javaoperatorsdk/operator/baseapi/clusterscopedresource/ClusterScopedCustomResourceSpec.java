package io.javaoperatorsdk.operator.baseapi.clusterscopedresource;

public class ClusterScopedCustomResourceSpec {

  private String data;
  private String targetNamespace;

  public String getData() {
    return data;
  }

  public ClusterScopedCustomResourceSpec setData(String data) {
    this.data = data;
    return this;
  }

  public String getTargetNamespace() {
    return targetNamespace;
  }

  public ClusterScopedCustomResourceSpec setTargetNamespace(String targetNamespace) {
    this.targetNamespace = targetNamespace;
    return this;
  }
}

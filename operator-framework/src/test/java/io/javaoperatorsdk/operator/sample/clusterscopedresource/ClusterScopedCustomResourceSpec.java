package io.javaoperatorsdk.operator.sample.clusterscopedresource;

public class ClusterScopedCustomResourceSpec {

  private String targetNamespace;

  public String getTargetNamespace() {
    return targetNamespace;
  }

  public ClusterScopedCustomResourceSpec setTargetNamespace(String targetNamespace) {
    this.targetNamespace = targetNamespace;
    return this;
  }
}

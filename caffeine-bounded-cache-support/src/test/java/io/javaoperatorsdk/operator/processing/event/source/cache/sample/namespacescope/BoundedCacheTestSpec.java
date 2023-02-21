package io.javaoperatorsdk.operator.processing.event.source.cache.sample.namespacescope;

public class BoundedCacheTestSpec {

  private String data;
  private String targetNamespace;

  public String getData() {
    return data;
  }

  public BoundedCacheTestSpec setData(String data) {
    this.data = data;
    return this;
  }

  public String getTargetNamespace() {
    return targetNamespace;
  }

  public BoundedCacheTestSpec setTargetNamespace(String targetNamespace) {
    this.targetNamespace = targetNamespace;
    return this;
  }
}

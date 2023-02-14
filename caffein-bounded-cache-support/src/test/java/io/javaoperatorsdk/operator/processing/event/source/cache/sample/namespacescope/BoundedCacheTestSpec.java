package io.javaoperatorsdk.operator.processing.event.source.cache.sample.namespacescope;

public class BoundedCacheTestSpec {

  private String data;

  public String getData() {
    return data;
  }

  public BoundedCacheTestSpec setData(String data) {
    this.data = data;
    return this;
  }
}

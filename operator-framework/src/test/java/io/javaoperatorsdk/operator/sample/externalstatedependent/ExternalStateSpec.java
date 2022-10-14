package io.javaoperatorsdk.operator.sample.externalstatedependent;

public class ExternalStateSpec {

  private String data;

  public String getData() {
    return data;
  }

  public ExternalStateSpec setData(String data) {
    this.data = data;
    return this;
  }
}

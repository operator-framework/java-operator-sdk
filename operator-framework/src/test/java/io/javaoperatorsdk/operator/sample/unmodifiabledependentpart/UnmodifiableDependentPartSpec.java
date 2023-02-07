package io.javaoperatorsdk.operator.sample.unmodifiabledependentpart;

public class UnmodifiableDependentPartSpec {

  private String data;

  public String getData() {
    return data;
  }

  public UnmodifiableDependentPartSpec setData(String data) {
    this.data = data;
    return this;
  }
}

package io.javaoperatorsdk.operator.baseapi.subresource;

public class SubResourceTestCustomResourceSpec {

  private String value;

  public String getValue() {
    return value;
  }

  public SubResourceTestCustomResourceSpec setValue(String value) {
    this.value = value;
    return this;
  }
}

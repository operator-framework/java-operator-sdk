package io.javaoperatorsdk.operator.baseapi.retry;

public class RetryTestCustomResourceSpec {

  private String value;

  public String getValue() {
    return value;
  }

  public RetryTestCustomResourceSpec setValue(String value) {
    this.value = value;
    return this;
  }
}

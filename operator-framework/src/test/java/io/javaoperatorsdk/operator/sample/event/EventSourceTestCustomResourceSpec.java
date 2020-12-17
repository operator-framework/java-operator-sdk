package io.javaoperatorsdk.operator.sample.event;

public class EventSourceTestCustomResourceSpec {

  private String value;

  public String getValue() {
    return value;
  }

  public EventSourceTestCustomResourceSpec setValue(String value) {
    this.value = value;
    return this;
  }
}

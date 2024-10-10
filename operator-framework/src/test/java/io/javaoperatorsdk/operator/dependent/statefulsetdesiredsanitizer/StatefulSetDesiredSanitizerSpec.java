package io.javaoperatorsdk.operator.dependent.statefulsetdesiredsanitizer;

public class StatefulSetDesiredSanitizerSpec {

  private String value;

  public String getValue() {
    return value;
  }

  public StatefulSetDesiredSanitizerSpec setValue(String value) {
    this.value = value;
    return this;
  }
}

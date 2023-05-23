package io.javaoperatorsdk.operator.sample.specialresourcesdependent;

public class SpecialResourceSpec {

  public static final String INITIAL_VALUE = "initial_val";
  public static final String CHANGED_VALUE = "changed_val";

  private String value;

  public String getValue() {
    return value;
  }

  public SpecialResourceSpec setValue(String value) {
    this.value = value;
    return this;
  }
}

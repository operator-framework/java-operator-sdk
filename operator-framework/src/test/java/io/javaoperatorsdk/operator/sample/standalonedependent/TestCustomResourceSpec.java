package io.javaoperatorsdk.operator.sample.standalonedependent;

import java.util.Objects;

public class TestCustomResourceSpec {

  private String value;

  public String getValue() {
    return value;
  }

  public TestCustomResourceSpec setValue(String value) {
    this.value = value;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TestCustomResourceSpec that = (TestCustomResourceSpec) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public String toString() {
    return "TestCustomResourceSpec{" +
            "value='" + value + '\'' +
            '}';
  }
}

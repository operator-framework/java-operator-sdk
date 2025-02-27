package io.javaoperatorsdk.operator.sample.simple;

import java.util.Objects;

public class TestCustomResourceSpec {

  private String configMapName;

  private String key;

  private String value;

  public String getConfigMapName() {
    return configMapName;
  }

  public void setConfigMapName(String configMapName) {
    this.configMapName = configMapName;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return "TestCustomResourceSpec{"
        + "configMapName='"
        + configMapName
        + '\''
        + ", key='"
        + key
        + '\''
        + ", value='"
        + value
        + '\''
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TestCustomResourceSpec that = (TestCustomResourceSpec) o;
    return Objects.equals(configMapName, that.configMapName)
        && Objects.equals(key, that.key)
        && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(configMapName, key, value);
  }
}

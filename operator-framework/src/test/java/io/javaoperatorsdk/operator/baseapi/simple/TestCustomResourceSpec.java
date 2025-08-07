package io.javaoperatorsdk.operator.baseapi.simple;

import java.util.Map;

import io.fabric8.crd.generator.annotation.PreserveUnknownFields;

public class TestCustomResourceSpec {

  private String configMapName;

  private String key;

  private String value;

  @PreserveUnknownFields private Map<String, Object> someValue;

  public Map<String, Object> getSomeValue() {
    return someValue;
  }

  public void setSomeValue(Map<String, Object> value) {
    this.someValue = value;
  }

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
}

package io.javaoperatorsdk.operator.baseapi.simple;

import com.fasterxml.jackson.databind.JsonNode;

public class TestCustomResourceSpec {

  private String configMapName;

  private String key;

  private String value;

  private JsonNode someValue;

  public JsonNode getSomeValue() {
    return someValue;
  }

  public void setSomeValue(JsonNode value) {
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

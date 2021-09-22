package io.javaoperatorsdk.operator.sample.informereventsource;

public class InformerEventSourceTestCustomResourceStatus {

  private String configMapValue;

  public String getConfigMapValue() {
    return configMapValue;
  }

  public InformerEventSourceTestCustomResourceStatus setConfigMapValue(String configMapValue) {
    this.configMapValue = configMapValue;
    return this;
  }
}

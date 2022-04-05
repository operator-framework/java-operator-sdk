package io.javaoperatorsdk.operator.sample.primaryindexer;

public class CustomFilteringTestResourceSpec {

  private String configMapName;

  public String getConfigMapName() {
    return configMapName;
  }

  public CustomFilteringTestResourceSpec setConfigMapName(String configMapName) {
    this.configMapName = configMapName;
    return this;
  }
}

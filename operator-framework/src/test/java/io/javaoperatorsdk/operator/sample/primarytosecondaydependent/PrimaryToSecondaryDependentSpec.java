package io.javaoperatorsdk.operator.sample.primarytosecondaydependent;

public class PrimaryToSecondaryDependentSpec {

  private String configMapName;

  public String getConfigMapName() {
    return configMapName;
  }

  public PrimaryToSecondaryDependentSpec setConfigMapName(String configMapName) {
    this.configMapName = configMapName;
    return this;
  }
}

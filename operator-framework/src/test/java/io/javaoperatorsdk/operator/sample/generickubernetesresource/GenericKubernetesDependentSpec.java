package io.javaoperatorsdk.operator.sample.generickubernetesresource;

public class GenericKubernetesDependentSpec {

  private String value;

  public String getValue() {
    return value;
  }

  public GenericKubernetesDependentSpec setValue(String value) {
    this.value = value;
    return this;
  }
}

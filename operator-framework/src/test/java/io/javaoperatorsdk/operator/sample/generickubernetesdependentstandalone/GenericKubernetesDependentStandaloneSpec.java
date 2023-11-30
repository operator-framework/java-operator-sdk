package io.javaoperatorsdk.operator.sample.generickubernetesdependentstandalone;

public class GenericKubernetesDependentStandaloneSpec {

  private String value;

  public String getValue() {
    return value;
  }

  public GenericKubernetesDependentStandaloneSpec setValue(String value) {
    this.value = value;
    return this;
  }
}

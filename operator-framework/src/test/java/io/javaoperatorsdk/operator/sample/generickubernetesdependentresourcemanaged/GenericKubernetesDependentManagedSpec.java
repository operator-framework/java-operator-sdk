package io.javaoperatorsdk.operator.sample.generickubernetesdependentresourcemanaged;

public class GenericKubernetesDependentManagedSpec {

  private String value;

  public String getValue() {
    return value;
  }

  public GenericKubernetesDependentManagedSpec setValue(String value) {
    this.value = value;
    return this;
  }
}

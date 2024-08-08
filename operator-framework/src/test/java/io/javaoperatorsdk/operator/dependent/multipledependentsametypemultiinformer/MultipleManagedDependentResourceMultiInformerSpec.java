package io.javaoperatorsdk.operator.dependent.multipledependentsametypemultiinformer;

public class MultipleManagedDependentResourceMultiInformerSpec {

  private String value;

  public String getValue() {
    return value;
  }

  public MultipleManagedDependentResourceMultiInformerSpec setValue(String value) {
    this.value = value;
    return this;
  }
}

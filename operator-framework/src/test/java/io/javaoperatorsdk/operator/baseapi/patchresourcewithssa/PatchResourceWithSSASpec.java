package io.javaoperatorsdk.operator.baseapi.patchresourcewithssa;

public class PatchResourceWithSSASpec {

  private String initValue;
  private String controllerManagedValue;

  public String getInitValue() {
    return initValue;
  }

  public void setInitValue(String initValue) {
    this.initValue = initValue;
  }

  public String getControllerManagedValue() {
    return controllerManagedValue;
  }

  public void setControllerManagedValue(String controllerManagedValue) {
    this.controllerManagedValue = controllerManagedValue;
  }
}

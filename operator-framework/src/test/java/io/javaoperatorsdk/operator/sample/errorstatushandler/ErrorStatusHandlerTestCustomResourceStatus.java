package io.javaoperatorsdk.operator.sample.errorstatushandler;

public class ErrorStatusHandlerTestCustomResourceStatus {

  private String message;

  public String getMessage() {
    return message;
  }

  public ErrorStatusHandlerTestCustomResourceStatus setMessage(String message) {
    this.message = message;
    return this;
  }
}

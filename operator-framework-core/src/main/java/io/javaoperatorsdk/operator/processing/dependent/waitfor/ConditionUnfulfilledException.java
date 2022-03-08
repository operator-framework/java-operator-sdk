package io.javaoperatorsdk.operator.processing.dependent.waitfor;

import io.javaoperatorsdk.operator.OperatorException;

public class ConditionUnfulfilledException extends OperatorException {

  private final UnfulfillmentHandler unfulfillmentHandler;

  public ConditionUnfulfilledException(UnfulfillmentHandler unfulfillmentHandler) {
    this.unfulfillmentHandler = unfulfillmentHandler;
  }

  public ConditionUnfulfilledException(String message,
      UnfulfillmentHandler unfulfillmentHandler) {
    super(message);
    this.unfulfillmentHandler = unfulfillmentHandler;
  }

  public UnfulfillmentHandler getUnfulfillmentHandler() {
    return unfulfillmentHandler;
  }
}

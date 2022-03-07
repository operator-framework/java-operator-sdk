package io.javaoperatorsdk.operator.processing.dependent.waitfor;

import io.javaoperatorsdk.operator.OperatorException;

public class ConditionNotFulfilledException extends OperatorException {

  private final UnfulfillmentHandler unFulfillmentHandler;

  public ConditionNotFulfilledException(UnfulfillmentHandler unFulfillmentHandler) {
    this.unFulfillmentHandler = unFulfillmentHandler;
  }

  public ConditionNotFulfilledException(String message,
      UnfulfillmentHandler unFulfillmentHandler) {
    super(message);
    this.unFulfillmentHandler = unFulfillmentHandler;
  }

  public UnfulfillmentHandler getNotMetConditionHandler() {
    return unFulfillmentHandler;
  }
}

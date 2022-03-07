package io.javaoperatorsdk.operator.processing.dependent.waitfor;

import io.javaoperatorsdk.operator.OperatorException;

public class ConditionNotFulfilledException extends OperatorException {

  private final ConditionNotFulfilledHandler conditionNotFulfilledHandler;

  public ConditionNotFulfilledException(ConditionNotFulfilledHandler conditionNotFulfilledHandler) {
    this.conditionNotFulfilledHandler = conditionNotFulfilledHandler;
  }

  public ConditionNotFulfilledException(String message,
      ConditionNotFulfilledHandler conditionNotFulfilledHandler) {
    super(message);
    this.conditionNotFulfilledHandler = conditionNotFulfilledHandler;
  }

  public ConditionNotFulfilledHandler getNotMetConditionHandler() {
    return conditionNotFulfilledHandler;
  }
}

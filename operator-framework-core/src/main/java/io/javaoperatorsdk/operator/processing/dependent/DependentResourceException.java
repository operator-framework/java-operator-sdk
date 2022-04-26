package io.javaoperatorsdk.operator.processing.dependent;

import io.javaoperatorsdk.operator.OperatorException;

public class DependentResourceException extends OperatorException {

  public DependentResourceException() {}

  public DependentResourceException(String message) {
    super(message);
  }

  public DependentResourceException(Throwable cause) {
    super(cause);
  }

  public DependentResourceException(String message, Throwable cause) {
    super(message, cause);
  }
}

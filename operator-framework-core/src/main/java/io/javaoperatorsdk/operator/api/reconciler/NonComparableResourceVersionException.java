package io.javaoperatorsdk.operator.api.reconciler;

import io.javaoperatorsdk.operator.OperatorException;

public class NonComparableResourceVersionException extends OperatorException {

  public NonComparableResourceVersionException(String message) {
    super(message);
  }
}

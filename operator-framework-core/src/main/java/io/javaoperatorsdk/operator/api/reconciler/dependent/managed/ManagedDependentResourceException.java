package io.javaoperatorsdk.operator.api.reconciler.dependent.managed;

import io.javaoperatorsdk.operator.OperatorException;

public class ManagedDependentResourceException extends OperatorException {
  private final String associatedDependentName;

  public ManagedDependentResourceException(
      String associatedDependentName, String message, Throwable cause) {
    super(message, cause);
    this.associatedDependentName = associatedDependentName;
  }

  public String getAssociatedDependentName() {
    return associatedDependentName;
  }
}

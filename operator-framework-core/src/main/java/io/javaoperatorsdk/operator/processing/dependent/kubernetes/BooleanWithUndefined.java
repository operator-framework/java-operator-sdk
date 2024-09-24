package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

/**
 * A replacement for {@link Boolean}, which can't be used in annotations.
 */
public enum BooleanWithUndefined {
  TRUE, FALSE, UNDEFINED;

  public Boolean asBoolean() {
    return switch (this) {
      case TRUE -> Boolean.TRUE;
      case FALSE -> Boolean.FALSE;
      default -> null;
    };
  }
}

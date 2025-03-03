package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

/** A replacement for {@link Boolean}, which can't be used in annotations. */
public enum BooleanWithUndefined {
  TRUE,
  FALSE,
  UNDEFINED;

  public Boolean asBoolean() {
    switch (this) {
      case TRUE:
        return Boolean.TRUE;
      case FALSE:
        return Boolean.FALSE;
      default:
        return null;
    }
  }
}

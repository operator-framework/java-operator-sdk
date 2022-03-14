package io.javaoperatorsdk.operator;

public class OperatorException extends RuntimeException {

  public OperatorException() {}

  public OperatorException(String message) {
    super(message);
  }

  public OperatorException(Throwable cause) {
    super(cause);
  }

  public OperatorException(String message, Throwable cause) {
    super(message, cause);
  }
}

package io.javaoperatorsdk.operator;

import java.util.Arrays;
import java.util.List;

public class AggregatedOperatorException extends OperatorException {

  private final List<Exception> causes;

  public AggregatedOperatorException(String message, Exception... exceptions) {
    super(message, exceptions[0]);
    this.causes = Arrays.asList(exceptions);
  }

  public AggregatedOperatorException(String message, List<Exception> exceptions) {
    super(message, exceptions.get(0));
    this.causes = exceptions;
  }

  public List<Exception> getAggregatedExceptions() {
    return causes;
  }

  @Override
  public String toString() {
    return "AggregatedOperatorException{" +
        "causes=" + causes +
        '}';
  }
}

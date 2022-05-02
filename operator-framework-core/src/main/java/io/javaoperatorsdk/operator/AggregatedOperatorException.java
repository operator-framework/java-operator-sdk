package io.javaoperatorsdk.operator;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class AggregatedOperatorException extends OperatorException {

  private final List<Exception> causes;

  public AggregatedOperatorException(String message, Exception... exceptions) {
    super(message, Objects.requireNonNull(exceptions)[0]);
    this.causes = Arrays.asList(exceptions);
  }

  public AggregatedOperatorException(String message, List<Exception> exceptions) {
    super(message, Objects.requireNonNull(exceptions).get(0));
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

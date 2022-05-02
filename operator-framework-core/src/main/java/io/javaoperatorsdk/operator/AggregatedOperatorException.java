package io.javaoperatorsdk.operator;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AggregatedOperatorException extends OperatorException {

  private final List<Exception> causes;

  public AggregatedOperatorException(String message, Exception... exceptions) {
    super(message, exceptions != null && exceptions.length > 0 ? exceptions[0] : null);
    this.causes = exceptions != null ? Arrays.asList(exceptions) : Collections.emptyList();
  }

  public AggregatedOperatorException(String message, List<Exception> exceptions) {
    super(message, exceptions != null && !exceptions.isEmpty() ? exceptions.get(0) : null);
    this.causes = exceptions != null ? exceptions : Collections.emptyList();
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

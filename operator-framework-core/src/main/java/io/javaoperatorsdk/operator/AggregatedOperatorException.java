package io.javaoperatorsdk.operator;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AggregatedOperatorException extends OperatorException {
  private final List<Exception> causes;

  public AggregatedOperatorException(String message, Exception... exceptions) {
    super(message);
    this.causes = exceptions != null ? Arrays.asList(exceptions) : Collections.emptyList();
  }

  public AggregatedOperatorException(String message, List<Exception> exceptions) {
    super(message);
    this.causes = exceptions != null ? exceptions : Collections.emptyList();
  }

  public List<Exception> getAggregatedExceptions() {
    return causes;
  }
}

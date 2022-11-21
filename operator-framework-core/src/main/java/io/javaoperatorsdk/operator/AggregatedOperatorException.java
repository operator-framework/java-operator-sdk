package io.javaoperatorsdk.operator;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public class AggregatedOperatorException extends OperatorException {

  private final Map<String, Exception> causes;

  public AggregatedOperatorException(String message, Map<String, Exception> exceptions) {
    super(message);
    this.causes =
        exceptions != null ? Collections.unmodifiableMap(exceptions) : Collections.emptyMap();
  }

  @SuppressWarnings("unused")
  public Map<String, Exception> getAggregatedExceptions() {
    return causes;
  }

  @Override
  public String getMessage() {
    return super.getMessage() + " " + causes.entrySet().stream()
        .map(entry -> entry.getKey() + " -> " + entry.getValue())
        .collect(Collectors.joining("\n - ", "Details:\n", ""));
  }
}

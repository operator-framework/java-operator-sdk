package io.javaoperatorsdk.operator;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
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
    return super.getMessage()
        + " "
        + causes.entrySet().stream()
            .map(entry -> entry.getKey() + " -> " + exceptionDescription(entry))
            .collect(Collectors.joining("\n - ", "Details:\n - ", ""));
  }

  private static String exceptionDescription(Entry<String, Exception> entry) {
    final var exception = entry.getValue();
    final var out = new StringWriter(2000);
    final var stringWriter = new PrintWriter(out);
    exception.printStackTrace(stringWriter);
    return out.toString();
  }
}

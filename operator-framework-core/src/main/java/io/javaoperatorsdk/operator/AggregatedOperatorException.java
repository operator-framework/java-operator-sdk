/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

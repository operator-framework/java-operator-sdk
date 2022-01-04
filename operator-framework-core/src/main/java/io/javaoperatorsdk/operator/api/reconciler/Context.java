package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;

public interface Context {

  Optional<RetryInfo> getRetryInfo();

  default <T> Optional<T> getSecondaryResource(Class<T> expectedType) {
    return getSecondaryResource(expectedType, null);
  }

  <T> Optional<T> getSecondaryResource(Class<T> expectedType, String eventSourceName);
}

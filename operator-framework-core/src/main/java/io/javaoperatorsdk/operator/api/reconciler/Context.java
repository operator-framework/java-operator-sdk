package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;

public interface Context {

  Optional<RetryInfo> getRetryInfo();

  <T> T getSecondaryResource(Class<T> expectedType, String... qualifier);
}

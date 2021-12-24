package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;

public interface Context extends AttributeHolder {

  Optional<RetryInfo> getRetryInfo();

  default <T> Optional<T> getSecondaryResource(Class<T> expectedType) {
    return getSecondaryResource(expectedType, null);
  }

  <T> Optional<T> getSecondaryResource(Class<T> expectedType, String eventSourceName);

  @Override
  default <T> T getMandatory(Object key, Class<T> expectedType) {
    return get(key, expectedType).orElseThrow(() -> new IllegalStateException(
        "Mandatory attribute (key: " + key + ", type: " + expectedType.getName()
            + ") is missing or not of the expected type"));
  }
}

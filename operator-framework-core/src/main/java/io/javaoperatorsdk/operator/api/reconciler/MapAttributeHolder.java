package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MapAttributeHolder {

  private final ConcurrentHashMap attributes = new ConcurrentHashMap();

  public <T> Optional<T> get(Object key, Class<T> expectedType) {
    return Optional.ofNullable(attributes.get(key))
        .filter(expectedType::isInstance)
        .map(expectedType::cast);
  }

  public Optional put(Object key, Object value) {
    if (value == null) {
      return Optional.ofNullable(attributes.remove(key));
    }
    return Optional.ofNullable(attributes.put(key, value));
  }

  public <T> T getMandatory(Object key, Class<T> expectedType) {
    return get(key, expectedType).orElseThrow(() -> new IllegalStateException(
        "Mandatory attribute (key: " + key + ", type: " + expectedType.getName()
            + ") is missing or not of the expected type"));
  }
}

package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;

public interface AttributeHolder {

  <T> Optional<T> get(Object key, Class<T> expectedType);

  <T> T getMandatory(Object key, Class<T> expectedType);

  Optional put(Object key, Object value);
}

package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface Cloner {

  /**
   * Returns a deep copy of the given object if not {@code null} or {@code null} otherwise.
   *
   * @param object the object to be cloned
   * @param <R> the type of the object to be cloned
   * @return a deep copy of the given object if it isn't {@code null}, {@code null} otherwise
   */
  <R extends HasMetadata> R clone(R object);

}

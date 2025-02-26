package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface Cloner {

  <R extends HasMetadata> R clone(R object);
}

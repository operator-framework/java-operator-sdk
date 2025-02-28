package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;

public interface Informable<R extends HasMetadata> {

  default String getResourceTypeName() {
    return getInformerConfig().getResourceTypeName();
  }

  InformerConfiguration<R> getInformerConfig();

  default Class<R> getResourceClass() {
    return getInformerConfig().getResourceClass();
  }
}

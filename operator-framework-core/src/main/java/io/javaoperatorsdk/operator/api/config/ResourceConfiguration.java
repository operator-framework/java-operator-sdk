package io.javaoperatorsdk.operator.api.config;


import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;

public interface ResourceConfiguration<R extends HasMetadata> {

  default String getResourceTypeName() {
    return ReconcilerUtils.getResourceTypeName(getResourceClass());
  }

  InformerConfiguration<R> getInformerConfig();

  @SuppressWarnings("unchecked")
  default Class<R> getResourceClass() {
    return (Class<R>) Utils.getFirstTypeArgumentFromSuperClassOrInterface(getClass(),
        ResourceConfiguration.class);
  }
}

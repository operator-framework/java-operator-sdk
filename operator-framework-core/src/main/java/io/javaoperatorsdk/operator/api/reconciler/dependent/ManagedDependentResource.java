package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigService;

@Retention(RetentionPolicy.RUNTIME)
public @interface ManagedDependentResource {

  Class<? extends DependentResourceInitializer<DependentResource<?, HasMetadata>, DependentResourceConfigService>> initializer();

}

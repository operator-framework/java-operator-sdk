package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface ManagedDependentResource {

  Class<? extends DependentResourceInitializer<?>> initializer();

}

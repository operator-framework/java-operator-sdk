package io.javaoperatorsdk.operator.api.config.dependent;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public @interface Dependent {

  Class<?> resourceType();

  Class<? extends DependentResource> type();
}

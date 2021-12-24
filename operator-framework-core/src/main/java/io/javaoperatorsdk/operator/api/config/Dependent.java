package io.javaoperatorsdk.operator.api.config;

public @interface Dependent {

  Class<?> resourceType();

  Class<? extends DependentResource> type();
}

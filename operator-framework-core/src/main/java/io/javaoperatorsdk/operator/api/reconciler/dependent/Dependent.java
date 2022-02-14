package io.javaoperatorsdk.operator.api.reconciler.dependent;

public @interface Dependent {

  Class<? extends DependentResource> type();
}

package io.javaoperatorsdk.operator.api.reconciler.dependent;

public interface ResourceTypeAware<R> {

  Class<R> resourceType();
}

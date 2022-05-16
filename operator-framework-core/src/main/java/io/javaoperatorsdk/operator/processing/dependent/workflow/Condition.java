package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public interface Condition<R, P extends HasMetadata> {

  boolean isMet(DependentResource<R, P> dependentResource, P primary, Context<P> context);
}

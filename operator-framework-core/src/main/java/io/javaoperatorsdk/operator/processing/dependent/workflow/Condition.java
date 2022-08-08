package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public interface Condition<R, P extends HasMetadata> {

  boolean isMet(P primary, Optional<R> secondary, Context<P> context);
}

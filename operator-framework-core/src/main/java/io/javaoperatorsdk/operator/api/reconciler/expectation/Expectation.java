package io.javaoperatorsdk.operator.api.reconciler.expectation;

import java.time.Duration;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface Expectation<P extends HasMetadata> {

  boolean isFulfilled(P primary, ExpectationContext<P> context);

  Duration timeout();
}

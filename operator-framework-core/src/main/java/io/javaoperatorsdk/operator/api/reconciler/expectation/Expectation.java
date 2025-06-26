package io.javaoperatorsdk.operator.api.reconciler.expectation;

import io.fabric8.kubernetes.api.model.HasMetadata;

import java.time.Duration;

public interface Expectation<P extends HasMetadata> {

  boolean isMet(P primary, ExpectationContext<P> context);

  Duration timeout();
}

package io.javaoperatorsdk.operator.api.reconciler.expectation;

import io.fabric8.kubernetes.api.model.HasMetadata;

public record ExpectationResult<P extends HasMetadata>(
    ExpectationStatus status, Expectation<P> expectation) {}

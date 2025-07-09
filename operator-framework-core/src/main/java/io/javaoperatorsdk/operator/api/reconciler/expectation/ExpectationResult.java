package io.javaoperatorsdk.operator.api.reconciler.expectation;

import io.fabric8.kubernetes.api.model.HasMetadata;

public record ExpectationResult<P extends HasMetadata, T extends Expectation<P>>(
    ExpectationStatus status, T expectation) {}

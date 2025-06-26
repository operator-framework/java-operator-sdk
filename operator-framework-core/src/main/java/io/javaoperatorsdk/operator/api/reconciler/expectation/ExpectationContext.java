package io.javaoperatorsdk.operator.api.reconciler.expectation;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.CacheAware;

public interface ExpectationContext<P extends HasMetadata> extends CacheAware<P> {}

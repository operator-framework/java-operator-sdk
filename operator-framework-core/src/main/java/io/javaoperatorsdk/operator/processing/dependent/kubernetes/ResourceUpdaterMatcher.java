package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public interface ResourceUpdaterMatcher<R extends HasMetadata> {

  R updateResource(R actual, R desired, Context<?> context);

  boolean matches(R actual, R desired, Context<?> context);
}

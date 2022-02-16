package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public interface ResourceMatcher {

  boolean match(HasMetadata actualResource, HasMetadata desiredResource, Context context);
}

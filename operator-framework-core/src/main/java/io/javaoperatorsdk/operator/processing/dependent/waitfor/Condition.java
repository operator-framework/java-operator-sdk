package io.javaoperatorsdk.operator.processing.dependent.waitfor;

import io.fabric8.kubernetes.api.model.HasMetadata;

@FunctionalInterface
public interface Condition<R, P extends HasMetadata> {

  boolean isFulfilled(R resource);

}

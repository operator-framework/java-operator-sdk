package io.javaoperatorsdk.operator.processing.dependent.waitfor;

import java.util.Optional;
import java.util.function.Supplier;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public interface Waiter<R, P extends HasMetadata> {

  void waitFor(DependentResource<R, P> resource, P primary, Condition<R, P> condition)
      throws ConditionNotFulfilledException;

  void waitFor(Supplier<Optional<R>> supplier, Condition<R, P> condition)
      throws ConditionNotFulfilledException;
}

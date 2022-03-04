package io.javaoperatorsdk.operator.processing.dependent.waitfor;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

import java.util.Optional;
import java.util.function.Supplier;

public interface Waiter<R,P extends HasMetadata> {

  void waitFor(DependentResource<R, P> resource, P primary, Condition<R, P> condition);

  void waitFor(Supplier<Optional<R>> supplier, P primary, Condition<R,P> condition);
}

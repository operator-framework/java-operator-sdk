package io.javaoperatorsdk.operator.processing.dependent.dependson;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public class DefaultWaiter<R,P extends HasMetadata> implements Waiter<R,P> {
    
    @Override
    public void waitFor(DependentResource<R, P> resource, Condition<R, P> condition) {

    }
}

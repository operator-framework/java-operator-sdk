package io.javaoperatorsdk.experimental.dependentresource;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

import java.util.Optional;


public abstract class DependentResource<D extends HasMetadata,P extends HasMetadata> {

    private final String name;
    private volatile ReconciliationResult lastReconciliationResult;

    public DependentResource(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public ReconciliationResult reconcile(ReconciliationContext<P> reconciliationContext) {
        lastReconciliationResult = reconcileResource(reconciliationContext);
        return lastReconciliationResult;
    }

    protected abstract ReconciliationResult reconcileResource(ReconciliationContext<P> reconciliationContext);

    public abstract Optional<D> getResource(P primaryResource);

    public abstract Optional<EventSource> eventSource();
}

package io.javaoperatorsdk.experimental.dependentresource;


import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import java.util.List;

public class ReconciliationContext<P extends HasMetadata> {

    private final P resource;
    private final Context context;
    private final List<DependentResource<? , P>> dependentResources;

    public ReconciliationContext(P resource, Context context, List<DependentResource<?, P>> dependentResources) {
        this.resource = resource;
        this.context = context;
        this.dependentResources = dependentResources;
    }

    public P getResource() {
        return resource;
    }

    public Context getContext() {
        return context;
    }

    public List<DependentResource<?, P>> getDependentResources() {
        return dependentResources;
    }
}

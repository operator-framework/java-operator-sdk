package io.javaoperatorsdk.experimental.dependentresource;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import java.util.Optional;

public abstract class KubernetesDependentResource<D extends HasMetadata, P extends HasMetadata> extends DependentResource<D,P> {

    private InformerEventSource<D,P> informerEventSource;
    protected final KubernetesClient kubernetesClient;


    public KubernetesDependentResource(String name, KubernetesClient kubernetesClient) {
        super(name);
        this.kubernetesClient = kubernetesClient;
        informerEventSource = initEventSource(kubernetesClient);
    }

    @Override
    protected ReconciliationResult reconcileResource(ReconciliationContext<P> reconciliationContext) {
        // todo compare spec (ability compare metadata)
        kubernetesClient.resources(resourceClass()).createOrReplace(targetResource());
        // todo successful action needs an additional check (custom), prepare design
        return ReconciliationResult.RECONCILIATION_SUCCESSFUL;
    }

    protected abstract D targetResource();

    @Override
    public Optional<D> getResource(P primaryResource) {
        return informerEventSource.getAssociated(primaryResource);
    }

    @Override
    public Optional<EventSource> eventSource() {
        return Optional.of(informerEventSource);
    }

    public Class<D> resourceClass() {
        return (Class<D>) Utils.getFirstTypeArgumentFromInterface(getClass());
    }

    // this could be made automatic
    protected abstract InformerEventSource<D,P> initEventSource(KubernetesClient kubernetesClient);

}

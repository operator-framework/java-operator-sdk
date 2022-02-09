package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public class StandaloneKubernetesDependentResource<R extends HasMetadata, P extends HasMetadata>
        extends KubernetesDependentResource<R,P> {

    private DesiredSupplier<R, P> desiredSupplier = null;
    private Class<R> resourceType;

    public StandaloneKubernetesDependentResource() {
    }

    public StandaloneKubernetesDependentResource(KubernetesClient client, DesiredSupplier<R, P> desiredSupplier,
                                                 Class<R> resourceType) {
        super(client);
        this.desiredSupplier = desiredSupplier;
        this.resourceType = resourceType;
    }

    @Override
    protected R desired(P primary, Context context) {
        if (desiredSupplier != null) {
            return desiredSupplier.getDesired(primary, context);
        } else {
            throw new OperatorException(
                    "No DesiredSupplier provided. Either provide one or override this method");
        }
    }

    public KubernetesDependentResource<R, P> setDesiredSupplier(
            DesiredSupplier<R, P> desiredSupplier) {
        this.desiredSupplier = desiredSupplier;
        return this;
    }

    public StandaloneKubernetesDependentResource<R, P> setResourceType(Class<R> resourceType) {
        this.resourceType = resourceType;
        return this;
    }

    public Class<R> resourceType() {
        return resourceType;
    }
}

package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.dependent.KubernetesDependentResourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.KubernetesDependentResource;

public class KubernetesDependentResourceInitializer extends
        DependentResourceInitializer<KubernetesDependentResource<?,?>, KubernetesDependentResourceConfiguration<?,?>> {

    @Override
    public KubernetesDependentResource<?, ?> initDependentResource(KubernetesDependentResourceConfiguration<?,?> config,
                                                            KubernetesClient client) {

        // todo with constructor, require a specific constructor args ?!
        KubernetesDependentResource kubernetesDependentResource = super.initDependentResource(config,client);
        kubernetesDependentResource.setClient(client);
        kubernetesDependentResource.initWithConfiguration(config);
        return kubernetesDependentResource;
    }

}

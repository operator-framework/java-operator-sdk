package com.github.containersolutions.operator.api;

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;

public class DefaultContext<T extends CustomResource> implements Context<T> {

    private final MixedOperation<T, KubernetesResourceList<T>, Doneable<T>, Resource<T, Doneable<T>>> customResourceClient;

    public DefaultContext(MixedOperation<T, KubernetesResourceList<T>, Doneable<T>, Resource<T, Doneable<T>>>
                                  customResourceClient) {
        this.customResourceClient = customResourceClient;
    }

    @Override
    public MixedOperation<T, KubernetesResourceList<T>, Doneable<T>, Resource<T, Doneable<T>>> getCustomResourceClient() {
        return customResourceClient;
    }
}

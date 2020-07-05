package com.github.containersolutions.operator.api;

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;

public interface Context<T extends CustomResource> {

    MixedOperation<T, KubernetesResourceList<T>, Doneable<T>, Resource<T, Doneable<T>>> getCustomResourceClient();

    RetryInfo retryInfo();

}

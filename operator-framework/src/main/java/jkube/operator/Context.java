package jkube.operator;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;

public class Context<R extends CustomResource> {

    private final KubernetesClient k8sClient;

    private final NonNamespaceOperation<R, CustomResourceList<R>, CustomResourceDoneable<R>,
            Resource<R, CustomResourceDoneable<R>>> resourceClient;


    public Context(KubernetesClient k8sClient, NonNamespaceOperation<R, CustomResourceList<R>, CustomResourceDoneable<R>, Resource<R, CustomResourceDoneable<R>>> resourceClient) {
        this.k8sClient = k8sClient;
        this.resourceClient = resourceClient;
    }

    public NonNamespaceOperation<R, CustomResourceList<R>, CustomResourceDoneable<R>, Resource<R, CustomResourceDoneable<R>>> getResourceClient() {
        return resourceClient;
    }

    public KubernetesClient getK8sClient() {
        return k8sClient;
    }
}

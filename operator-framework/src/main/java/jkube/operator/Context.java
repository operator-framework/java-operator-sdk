package jkube.operator;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;

public class Context<R extends CustomResource> {

    private final NonNamespaceOperation<R, CustomResourceList<R>, CustomResourceDoneable<R>,
            Resource<R, CustomResourceDoneable<R>>> resourceClient;


    public Context(NonNamespaceOperation<R, CustomResourceList<R>, CustomResourceDoneable<R>, Resource<R, CustomResourceDoneable<R>>> resourceClient) {
        this.resourceClient = resourceClient;
    }

    public NonNamespaceOperation<R, CustomResourceList<R>, CustomResourceDoneable<R>, Resource<R, CustomResourceDoneable<R>>> getResourceClient() {
        return resourceClient;
    }
}

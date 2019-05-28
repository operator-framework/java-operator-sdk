package jkube.operator;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.fabric8.kubernetes.client.CustomResourceList;

public interface CustomResourceController<R extends CustomResource, L extends CustomResourceList<R>, D extends CustomResourceDoneable<R>> {

    void deleteResource(R resource);

    R createOrUpdateResource(R resource);

}

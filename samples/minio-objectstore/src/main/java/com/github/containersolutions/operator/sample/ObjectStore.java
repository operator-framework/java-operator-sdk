package com.github.containersolutions.operator.sample;

import io.fabric8.kubernetes.client.CustomResource;

public class ObjectStore extends CustomResource {

    private ObjectStoreSpec spec;

    private ObjectStoreStatus status;

    public ObjectStoreSpec getSpec() {
        return spec;
    }

    public void setSpec(ObjectStoreSpec spec) {
        this.spec = spec;
    }

    public ObjectStoreStatus getStatus() {
        return status;
    }

    public void setStatus(ObjectStoreStatus status) {
        this.status = status;
    }
}

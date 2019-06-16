package com.github.containersolutions.operator.sample;

import io.fabric8.kubernetes.client.CustomResource;

public class CustomService extends CustomResource {

    private ServiceSpec spec;

    public ServiceSpec getSpec() {
        return spec;
    }

    public void setSpec(ServiceSpec spec) {
        this.spec = spec;
    }
}

package com.github.containersolutions.operator.sample;

import io.fabric8.kubernetes.client.CustomResource;

public class MinioInstance extends CustomResource {
    private MinoInstanceSpec spec;
    
    public MinoInstanceSpec getSpec() {
        return spec;
    }

    public void setSpec(MinoInstanceSpec spec) {
        this.spec = spec;
    }

}

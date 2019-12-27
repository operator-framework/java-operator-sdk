package com.github.containersolutions.operator.sample;

import io.fabric8.kubernetes.client.CustomResource;

public class TestCustomResource extends CustomResource {

    private TestCustomResourceSpec spec;

    private TestCustomResourceStatus status;

    public TestCustomResourceSpec getSpec() {
        return spec;
    }

    public void setSpec(TestCustomResourceSpec spec) {
        this.spec = spec;
    }

    public TestCustomResourceStatus getStatus() {
        return status;
    }

    public void setStatus(TestCustomResourceStatus status) {
        this.status = status;
    }
}
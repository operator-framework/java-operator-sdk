package com.example.jaxdemo;

import io.fabric8.kubernetes.client.CustomResource;

public class Webapp extends CustomResource {

    private WebappSpec spec;

    private WebappStatus status;

    public WebappSpec getSpec() {
        return spec;
    }

    public void setSpec(WebappSpec spec) {
        this.spec = spec;
    }

    public WebappStatus getStatus() {
        if (status == null) {
            status = new WebappStatus();
        }
        return status;
    }

    public void setStatus(WebappStatus status) {
        this.status = status;
    }
}
package com.example.jaxdemo;

import io.fabric8.kubernetes.client.CustomResource;

public class Tomcat extends CustomResource {

    private TomcatSpec spec;

    private TomcatStatus status;

    public TomcatSpec getSpec() {
        if (spec == null) {
            spec = new TomcatSpec();
        }
        return spec;
    }

    public void setSpec(TomcatSpec spec) {
        this.spec = spec;
    }

    public TomcatStatus getStatus() {
        if (status == null) {
            status = new TomcatStatus();
        }
        return status;
    }

    public void setStatus(TomcatStatus status) {
        this.status = status;
    }
}
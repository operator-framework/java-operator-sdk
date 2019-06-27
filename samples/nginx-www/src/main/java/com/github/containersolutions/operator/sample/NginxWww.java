package com.github.containersolutions.operator.sample;

import io.fabric8.kubernetes.client.CustomResource;

public class NginxWww extends CustomResource {

    private NginxWwwSpec spec;

    private NginxWwwStatus status;

    public NginxWwwSpec getSpec() {
        return spec;
    }

    public void setSpec(NginxWwwSpec spec) {
        this.spec = spec;
    }

    public NginxWwwStatus getStatus() {
        return status;
    }

    public void setStatus(NginxWwwStatus status) {
        this.status = status;
    }
}

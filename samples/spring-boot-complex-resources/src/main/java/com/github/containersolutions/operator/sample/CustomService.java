package com.github.containersolutions.operator.sample;

import io.fabric8.kubernetes.api.model.ServiceStatus;
import io.fabric8.kubernetes.client.CustomResource;

public class CustomService extends CustomResource {

    private ServiceSpec spec;
    
    private ServiceStatus status;

    public ServiceSpec getSpec() {
        return spec;
    }

    public void setSpec(ServiceSpec spec) {
        this.spec = spec;
    }

	public ServiceStatus getStatus() {
		return status;
	}

	public void setStatus(ServiceStatus status) {
		this.status = status;
	}
}

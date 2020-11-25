package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;

public class CustomService extends CustomResource implements Namespaced {
    
    private ServiceSpec spec;
    
    public ServiceSpec getSpec() {
        return spec;
    }
    
    public void setSpec(ServiceSpec spec) {
        this.spec = spec;
    }
    
    @Override
    public String getApiVersion() {
        return "sample.javaoperatorsdk.io/v1";
    }
}

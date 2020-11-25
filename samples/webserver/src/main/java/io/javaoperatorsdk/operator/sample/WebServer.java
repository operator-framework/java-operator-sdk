package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;

public class WebServer extends CustomResource implements Namespaced {
    
    private WebServerSpec spec;
    
    private WebServerStatus status;
    
    public WebServerSpec getSpec() {
        return spec;
    }
    
    public void setSpec(WebServerSpec spec) {
        this.spec = spec;
    }
    
    public WebServerStatus getStatus() {
        return status;
    }
    
    public void setStatus(WebServerStatus status) {
        this.status = status;
    }
    
    @Override
    public String getApiVersion() {
        return "sample.javaoperatorsdk.io/v1";
    }
}

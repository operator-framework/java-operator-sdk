package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;

public class Webapp extends CustomResource implements Namespaced {
    
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
    
    @Override
    public String getApiVersion() {
        return "tomcatoperator.io/v1";
    }
}
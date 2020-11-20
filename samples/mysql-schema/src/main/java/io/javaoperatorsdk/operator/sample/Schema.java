package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;

public class Schema extends CustomResource implements Namespaced {
    
    private SchemaSpec spec;
    
    private SchemaStatus status;
    
    public SchemaSpec getSpec() {
        return spec;
    }
    
    public void setSpec(SchemaSpec spec) {
        this.spec = spec;
    }
    
    public SchemaStatus getStatus() {
        return status;
    }
    
    public void setStatus(SchemaStatus status) {
        this.status = status;
    }
    
    @Override
    public String getApiVersion() {
        return "mysql.sample.javaoperatorsdk/v1";
    }
}

package io.javaoperatorsdk.operator.sample.subresource;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;

public class SubResourceTestCustomResource extends CustomResource implements Namespaced {
    
    private SubResourceTestCustomResourceSpec spec;
    
    private SubResourceTestCustomResourceStatus status;
    
    public SubResourceTestCustomResourceSpec getSpec() {
        return spec;
    }
    
    public void setSpec(SubResourceTestCustomResourceSpec spec) {
        this.spec = spec;
    }

    public SubResourceTestCustomResourceStatus getStatus() {
        return status;
    }

    public void setStatus(SubResourceTestCustomResourceStatus status) {
        this.status = status;
    }
    
    @Override
    public String toString() {
        return "TestCustomResource{" +
            "spec=" + spec +
            ", status=" + status +
            ", extendedFrom=" + super.toString() +
            '}';
    }
    
    @Override
    public String getKind() {
        return "SubresourceSample";
    }
    
    @Override
    public String getApiVersion() {
        return "sample.javaoperatorsdk.io/v1";
    }
}

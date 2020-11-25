package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;

public class TestCustomResource extends CustomResource implements Namespaced {
    
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
        return "CustomService";
    }
    
    @Override
    public String getApiVersion() {
        return "sample.javaoperatorsdk.io/v1";
    }
}

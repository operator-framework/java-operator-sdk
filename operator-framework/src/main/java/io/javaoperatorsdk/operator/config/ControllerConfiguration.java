package io.javaoperatorsdk.operator.config;

import io.fabric8.kubernetes.client.CustomResource;

public interface ControllerConfiguration<R extends CustomResource> {
    String getName();
    
    String getCRDName();
    
    String getFinalizer();
    
    boolean isGenerationAware();
    
    Class<R> getCustomResourceClass();
}

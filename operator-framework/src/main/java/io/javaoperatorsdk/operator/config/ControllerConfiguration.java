package io.javaoperatorsdk.operator.config;

import java.util.Collections;
import java.util.Set;

import io.fabric8.kubernetes.client.CustomResource;

public interface ControllerConfiguration<R extends CustomResource> {
    String getName();
    
    String getCRDName();
    
    String getFinalizer();
    
    boolean isGenerationAware();
    
    Class<R> getCustomResourceClass();
    
    default boolean isClusterScoped() {
        return false;
    }
    
    default Set<String> getNamespaces() {
        return Collections.emptySet();
    }
    
    default boolean watchAllNamespaces() {
        return false;
    }
    
    default RetryConfiguration getRetryConfiguration() {
        return new RetryConfiguration() {
        };
    }
}

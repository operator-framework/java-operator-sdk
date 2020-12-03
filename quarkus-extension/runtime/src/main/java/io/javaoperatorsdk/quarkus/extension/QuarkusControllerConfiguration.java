package io.javaoperatorsdk.quarkus.extension;

import java.util.Collections;
import java.util.Set;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.javaoperatorsdk.operator.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.config.RetryConfiguration;

public class QuarkusControllerConfiguration<R extends CustomResource> implements ControllerConfiguration<R> {
    private final String name;
    private final String crdName;
    private final String finalizer;
    private final boolean generationAware;
    private final boolean clusterScoped;
    private final Set<String> namespaces;
    private final Class<R> crClass;
    private final Class<CustomResourceDoneable<R>> doneableClass;
    private final boolean watchAllNamespaces;
    private final RetryConfiguration retryConfiguration;
    
    public QuarkusControllerConfiguration(String name, String crdName, String finalizer, boolean generationAware, boolean clusterScoped, String[] namespaces, Class<R> crClass, Class<CustomResourceDoneable<R>> doneableClass, RetryConfiguration retryConfiguration) {
        this.name = name;
        this.crdName = crdName;
        this.finalizer = finalizer;
        this.generationAware = generationAware;
        this.clusterScoped = clusterScoped;
        this.namespaces = namespaces == null || namespaces.length == 0 ? Collections.emptySet() : Set.of(namespaces);
        this.crClass = crClass;
        this.doneableClass = doneableClass;
        this.watchAllNamespaces = this.namespaces.contains(WATCH_ALL_NAMESPACES_MARKER);
        this.retryConfiguration = retryConfiguration == null ? ControllerConfiguration.super.getRetryConfiguration() : retryConfiguration;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getCRDName() {
        return crdName;
    }
    
    @Override
    public String getFinalizer() {
        return finalizer;
    }
    
    @Override
    public boolean isGenerationAware() {
        return generationAware;
    }
    
    @Override
    public Class<R> getCustomResourceClass() {
        return crClass;
    }
    
    @Override
    public Class<? extends CustomResourceDoneable<R>> getDoneableClass() {
        return doneableClass;
    }
    
    @Override
    public boolean isClusterScoped() {
        return clusterScoped;
    }
    
    @Override
    public Set<String> getNamespaces() {
        return namespaces;
    }
    
    @Override
    public boolean watchAllNamespaces() {
        return watchAllNamespaces;
    }
    
    @Override
    public RetryConfiguration getRetryConfiguration() {
        return retryConfiguration;
    }
}

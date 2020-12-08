package io.javaoperatorsdk.quarkus.extension;

import java.util.Collections;
import java.util.Set;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;

public class QuarkusControllerConfiguration<R extends CustomResource> implements ControllerConfiguration<R> {
    private String name;
    private String crdName;
    private String finalizer;
    private boolean generationAware;
    private boolean clusterScoped;
    private Set<String> namespaces;
    private Class<R> crClass;
    private Class<CustomResourceDoneable<R>> doneableClass;
    private boolean watchAllNamespaces;
    private RetryConfiguration retryConfiguration;
    
    // For serialization
    public QuarkusControllerConfiguration() {
    }
    
    public QuarkusControllerConfiguration(String name, String crdName, String finalizer, boolean generationAware, boolean clusterScoped, String[] namespaces, Class crClass, String doneableClass, RetryConfiguration retryConfiguration) {
        this.name = name;
        this.crdName = crdName;
        this.finalizer = finalizer;
        this.generationAware = generationAware;
        this.clusterScoped = clusterScoped;
        this.namespaces = namespaces == null || namespaces.length == 0 ? Collections.emptySet() : Set.of(namespaces);
        this.crClass = crClass;
        try {
            this.doneableClass = (Class<CustomResourceDoneable<R>>) Thread.currentThread().getContextClassLoader().loadClass(doneableClass);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Couldn't find class " + doneableClass);
        }
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
    
    // For serialization
    public void setName(String name) {
        this.name = name;
    }
    
    // For serialization
    public void setCrdName(String crdName) {
        this.crdName = crdName;
    }
    
    // For serialization
    public void setFinalizer(String finalizer) {
        this.finalizer = finalizer;
    }
    
    // For serialization
    public void setGenerationAware(boolean generationAware) {
        this.generationAware = generationAware;
    }
    
    // For serialization
    public void setClusterScoped(boolean clusterScoped) {
        this.clusterScoped = clusterScoped;
    }
    
    // For serialization
    public void setNamespaces(Set<String> namespaces) {
        this.namespaces = namespaces;
    }
    
    // For serialization
    public void setCrClass(Class<R> crClass) {
        this.crClass = crClass;
    }
    
    // For serialization
    public void setDoneableClass(Class<CustomResourceDoneable<R>> doneableClass) {
        this.doneableClass = doneableClass;
    }
    
    // For serialization
    public void setWatchAllNamespaces(boolean watchAllNamespaces) {
        this.watchAllNamespaces = watchAllNamespaces;
    }
    
    // For serialization
    public void setRetryConfiguration(RetryConfiguration retryConfiguration) {
        this.retryConfiguration = retryConfiguration;
    }
}

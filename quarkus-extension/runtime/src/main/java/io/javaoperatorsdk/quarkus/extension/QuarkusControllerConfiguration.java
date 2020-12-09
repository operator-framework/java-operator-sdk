package io.javaoperatorsdk.quarkus.extension;

import java.util.Collections;
import java.util.Set;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class QuarkusControllerConfiguration<R extends CustomResource> implements ControllerConfiguration<R> {
    private final String name;
    private final String crdName;
    private final String finalizer;
    private final boolean generationAware;
    private final boolean clusterScoped;
    private final Set<String> namespaces;
    private final String crClass;
    private final String doneableClassName;
    private final boolean watchAllNamespaces;
    private final RetryConfiguration retryConfiguration;
    
    @RecordableConstructor
    public QuarkusControllerConfiguration(String name, String crdName, String finalizer, boolean generationAware, boolean clusterScoped, Set<String> namespaces, String crClass, String doneableClassName, RetryConfiguration retryConfiguration) {
        this.name = name;
        this.crdName = crdName;
        this.finalizer = finalizer;
        this.generationAware = generationAware;
        this.clusterScoped = clusterScoped;
        this.namespaces = namespaces;
        this.crClass = crClass;
        this.doneableClassName = doneableClassName;
        this.watchAllNamespaces = this.namespaces.contains(WATCH_ALL_NAMESPACES_MARKER);
        this.retryConfiguration = retryConfiguration == null ? ControllerConfiguration.super.getRetryConfiguration() : retryConfiguration;
    }
    
    public static Set<String> asSet(String[] namespaces) {
        return namespaces == null || namespaces.length == 0 ? Collections.emptySet() : Set.of(namespaces);
    }
    
    public String getCrdName() {
        return getCRDName();
    }
    
    public String getCrClass() {
        return crClass;
    }
    
    public String getDoneableClassName() {
        return doneableClassName;
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
        try {
            return (Class<R>) Thread.currentThread().getContextClassLoader().loadClass(crClass);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Couldn't find class " + crClass);
        }
    }
    
    @Override
    public Class<? extends CustomResourceDoneable<R>> getDoneableClass() {
        try {
            return (Class<CustomResourceDoneable<R>>) Thread.currentThread().getContextClassLoader().loadClass(doneableClassName);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Couldn't find class " + doneableClassName);
        }
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

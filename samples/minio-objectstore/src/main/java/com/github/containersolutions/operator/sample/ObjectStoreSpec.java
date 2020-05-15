package com.github.containersolutions.operator.sample;

public class ObjectStoreSpec {
    private String name;
    private String deployNamespace;
    private Instances instances;
    private Volumes volumes;
    private Stores stores;
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDeployNamespace() {
        return deployNamespace;
    }
    
    public void setDeployNamespace(String deployNamespace) {
        this.deployNamespace = deployNamespace;
    }
    
    public Instances getInstances() {
        return instances;
    }
    
    public void setInstances(Instances instances) {
        this.instances = instances;
    }
    
    public Volumes getVolumes() {
        return volumes;
    }
    
    public void setVolumes(Volumes volumes) {
        this.volumes = volumes;
    }
    
    public Stores getStores() {
        return stores;
    }

    public void setStores(Stores stores) {
        this.stores = stores;
    }
    
}

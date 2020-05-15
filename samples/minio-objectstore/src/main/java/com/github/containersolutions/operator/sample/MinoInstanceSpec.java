package com.github.containersolutions.operator.sample;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ResourceRequirements;

@JsonIgnoreProperties(ignoreUnknown=true)
public class MinoInstanceSpec {
    private LabelSelector selector;
    private ObjectMeta metadata;    
    private String image;
    private String serviceName;
    private List<Zone> zones = new ArrayList();
    private List<EnvVar> env = new ArrayList();
    private Integer volumesPerServer;
    private String mountPath;
    private CredsSecret credsSecret;
    private CertConfig certConfig;
    private PersistentVolumeClaim volumeClaimTemplate;
    private String podManagementPolicy;
    private Boolean requestAutoCert;
    private ResourceRequirements resources;
    private Probe readiness;
    private Probe liveness;
    
    public LabelSelector getSelector() {
        return selector;
    }
    
    public void setSelector(LabelSelector selector) {
        this.selector = selector;
    }
    
    public ObjectMeta getMetadata() {
        return metadata;
    }
    
    public void setMetadata(ObjectMeta metadata) {
        this.metadata = metadata;
    }
    
    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getImage() {
        return image;
    }
    
    public void setImage(String image) {
        this.image = image;
    }
    
    public List<Zone> getZones() {
        return zones;
    }
    
    public void setZones(List<Zone> zones) {
        this.zones = zones;
    }
    
    public List<EnvVar> getEnv() {
        return env;
    }
    
    public void setEnv(List<EnvVar> env) {
        this.env = env;
    }
    
    public Integer getVolumesPerServer() {
        return volumesPerServer;
    }
    
    public void setVolumesPerServer(Integer volumesPerServer) {
        this.volumesPerServer = volumesPerServer;
    }
    
    public String getMountPath() {
        return mountPath;
    }
    
    public void setMountPath(String mountPath) {
        this.mountPath = mountPath;
    }
    
    public CredsSecret getCredsSecret() {
        return credsSecret;
    }
    
    public void setCredsSecret(CredsSecret credsSecret) {
        this.credsSecret = credsSecret;
    }
    
    public CertConfig getCertConfig() {
        return certConfig;
    }
    
    public void setCertConfig(CertConfig certConfig) {
        this.certConfig = certConfig;
    }
    
    public PersistentVolumeClaim getVolumeClaimTemplate() {
        return volumeClaimTemplate;
    }
    
    public void setVolumeClaimTemplate(PersistentVolumeClaim volumeClaimTemplate) {
        this.volumeClaimTemplate = volumeClaimTemplate;
    }
    
    public String getPodManagementPolicy() {
        return podManagementPolicy;
    }
    
    public void setPodManagementPolicy(String podManagementPolicy) {
        this.podManagementPolicy = podManagementPolicy;
    }
    
    public Boolean getRequestAutoCert() {
        return requestAutoCert;
    }
    
    public void setRequestAutoCert(Boolean requestAutoCert) {
        this.requestAutoCert = requestAutoCert;
    }
    
    public ResourceRequirements getResources() {
        return resources;
    }
    
    public void setResources(ResourceRequirements resources) {
        this.resources = resources;
    }
    
    public Probe getReadiness() {
        return readiness;
    }
    
    public void setReadiness(Probe readiness) {
        this.readiness = readiness;
    }
    
    public Probe getLiveness() {
        return liveness;
    }
 
    public void setLiveness(Probe liveness) {
        this.liveness = liveness;
    }
}

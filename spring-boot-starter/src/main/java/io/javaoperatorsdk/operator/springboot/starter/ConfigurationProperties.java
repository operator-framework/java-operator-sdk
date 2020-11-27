package io.javaoperatorsdk.operator.springboot.starter;

import java.util.Collections;
import java.util.Map;

@org.springframework.boot.context.properties.ConfigurationProperties(prefix = "io.javaoperatorsdk")
public class ConfigurationProperties {
    private OperatorProperties client = new OperatorProperties();
    private Map<String, ControllerProperties> controllers = Collections.emptyMap();
    
    // todo: figure out how to be able to use `.kubernetes.client` as prefix
    public OperatorProperties getClient() {
        return client;
    }
    
    public void setClient(OperatorProperties client) {
        this.client = client;
    }
    
    public Map<String, ControllerProperties> getControllers() {
        return controllers;
    }
    
    public void setControllers(Map<String, ControllerProperties> controllers) {
        this.controllers = controllers;
    }
}

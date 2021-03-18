package io.javaoperatorsdk.operator.springboot.starter;

import java.util.Collections;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "javaoperatorsdk")
public class OperatorConfigurationProperties {

  private KubernetesClientProperties client = new KubernetesClientProperties();
  private Map<String, ControllerProperties> controllers = Collections.emptyMap();
  private boolean checkCrdAndValidateLocalModel = true;

  public KubernetesClientProperties getClient() {
    return client;
  }

  public void setClient(KubernetesClientProperties client) {
    this.client = client;
  }

  public Map<String, ControllerProperties> getControllers() {
    return controllers;
  }

  public void setControllers(Map<String, ControllerProperties> controllers) {
    this.controllers = controllers;
  }

  public boolean getCheckCrdAndValidateLocalModel() {
    return checkCrdAndValidateLocalModel;
  }

  public void setCheckCrdAndValidateLocalModel(boolean checkCrdAndValidateLocalModel) {
    this.checkCrdAndValidateLocalModel = checkCrdAndValidateLocalModel;
  }
}

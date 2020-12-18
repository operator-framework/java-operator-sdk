package io.javaoperatorsdk.operator.springboot.starter;

import java.util.Set;

public class ControllerProperties {
  private String name;
  private String crdName;
  private String finalizer;
  private boolean generationAware;
  private boolean clusterScoped;
  private Set<String> namespaces;
  private RetryProperties retry;

  public String getName() {
    return name;
  }

  public String getCRDName() {
    return crdName;
  }

  public String getFinalizer() {
    return finalizer;
  }

  public boolean isGenerationAware() {
    return generationAware;
  }

  public boolean isClusterScoped() {
    return clusterScoped;
  }

  public Set<String> getNamespaces() {
    return namespaces;
  }

  public RetryProperties getRetry() {
    return retry;
  }

  public void setRetry(RetryProperties retry) {
    this.retry = retry;
  }
}

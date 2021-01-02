package io.javaoperatorsdk.operator.springboot.starter.test;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("javaoperatorsdk.test")
public class TestConfigurationProperties {

  private List<String> globalCrdPaths = new ArrayList<>();

  private List<String> crdPaths = new ArrayList<>();

  public List<String> getCrdPaths() {
    return crdPaths;
  }

  public void setCrdPaths(List<String> crdPaths) {
    this.crdPaths = crdPaths;
  }

  public List<String> getGlobalCrdPaths() {
    return globalCrdPaths;
  }

  public void setGlobalCrdPaths(List<String> globalCrdPaths) {
    this.globalCrdPaths = globalCrdPaths;
  }
}

package io.javaoperatorsdk.operator.sample.customresource;

import io.fabric8.crd.generator.annotation.PreserveUnknownFields;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;

public class WebPageSpec {

  private String html;
  private Boolean exposed = false;

  @PreserveUnknownFields
  private GenericKubernetesResource resource;

  public String getHtml() {
    return html;
  }

  public void setHtml(String html) {
    this.html = html;
  }

  public Boolean getExposed() {
    return exposed;
  }

  public WebPageSpec setExposed(Boolean exposed) {
    this.exposed = exposed;
    return this;
  }

  public GenericKubernetesResource getResource() {
    return resource;
  }

  public void setResource(GenericKubernetesResource resource) {
    this.resource = resource;
  }

  @Override
  public String toString() {
    return "WebPageSpec{" +
        "html='" + html + '\'' +
        '}';
  }
}

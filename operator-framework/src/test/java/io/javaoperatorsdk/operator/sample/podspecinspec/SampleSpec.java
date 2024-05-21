package io.javaoperatorsdk.operator.sample.podspecinspec;

import io.fabric8.crd.generator.annotation.SchemaFrom;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;

public class SampleSpec {

  @SchemaFrom(type = Pod.class)
  private PodTemplateSpec podTemplate;

  public PodTemplateSpec getPodTemplate() {
    return podTemplate;
  }

  public void setPodTemplate(PodTemplateSpec podTemplate) {
    this.podTemplate = podTemplate;
  }
}

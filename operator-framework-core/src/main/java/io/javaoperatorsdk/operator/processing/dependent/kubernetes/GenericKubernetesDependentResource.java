package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;

public class GenericKubernetesDependentResource<P extends HasMetadata>
    extends KubernetesDependentResource<GenericKubernetesResource, P> {

  private final String apiVersion;
  private final String kind;


  public GenericKubernetesDependentResource(String apiVersion, String kind) {
    super(GenericKubernetesResource.class);
    this.apiVersion = apiVersion;
    this.kind = kind;
  }



}

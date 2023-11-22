package io.javaoperatorsdk.operator.sample.generickubernetesdependentstandalone;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesDependentResource;

public class ConfigMapGenericKubernetesDependent extends
    GenericKubernetesDependentResource<GenericKubernetesDependentStandaloneCustomResource>
    implements
    Creator<GenericKubernetesResource, GenericKubernetesDependentStandaloneCustomResource>,
    Updater<GenericKubernetesResource, GenericKubernetesDependentStandaloneCustomResource>,
    Deleter<GenericKubernetesDependentStandaloneCustomResource> {


  public ConfigMapGenericKubernetesDependent() {
    super("v1", "ConfigMap");
  }

  @Override
  protected GenericKubernetesResource desired(
      GenericKubernetesDependentStandaloneCustomResource primary,
      Context<GenericKubernetesDependentStandaloneCustomResource> context) {

    return null;
  }


}

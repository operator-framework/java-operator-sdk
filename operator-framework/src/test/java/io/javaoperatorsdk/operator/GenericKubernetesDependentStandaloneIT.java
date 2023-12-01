package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.generickubernetesresource.GenericKubernetesDependentSpec;
import io.javaoperatorsdk.operator.sample.generickubernetesresource.generickubernetesdependentstandalone.GenericKubernetesDependentStandaloneCustomResource;
import io.javaoperatorsdk.operator.sample.generickubernetesresource.generickubernetesdependentstandalone.GenericKubernetesDependentStandaloneReconciler;

public class GenericKubernetesDependentStandaloneIT
    extends GenericKubernetesDependentTestBase<GenericKubernetesDependentStandaloneCustomResource> {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new GenericKubernetesDependentStandaloneReconciler())
          .build();

  @Override
  public LocallyRunOperatorExtension extension() {
    return extension;
  }

  @Override
  GenericKubernetesDependentStandaloneCustomResource testResource(String name, String data) {
    var resource = new GenericKubernetesDependentStandaloneCustomResource();
    resource.setMetadata(new ObjectMetaBuilder()
        .withName(name)
        .build());
    resource.setSpec(new GenericKubernetesDependentSpec());
    resource.getSpec().setValue(INITIAL_DATA);
    return resource;
  }
}

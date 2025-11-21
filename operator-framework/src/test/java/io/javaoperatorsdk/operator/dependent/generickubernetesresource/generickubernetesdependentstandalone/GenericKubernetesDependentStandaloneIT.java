package io.javaoperatorsdk.operator.dependent.generickubernetesresource.generickubernetesdependentstandalone;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.dependent.generickubernetesresource.GenericKubernetesDependentSpec;
import io.javaoperatorsdk.operator.dependent.generickubernetesresource.GenericKubernetesDependentTestBase;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

@Sample(
    tldr = "Generic Kubernetes Resource as Standalone Dependent",
    description =
        """
        Tests using GenericKubernetesResource as a standalone dependent resource. This approach \
        allows operators to manage arbitrary Kubernetes resources without requiring specific Java \
        classes for each resource type, providing flexibility for managing various resource types \
        dynamically.
        """)
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
  public GenericKubernetesDependentStandaloneCustomResource testResource(String name, String data) {
    var resource = new GenericKubernetesDependentStandaloneCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(name).build());
    resource.setSpec(new GenericKubernetesDependentSpec());
    resource.getSpec().setValue(INITIAL_DATA);
    return resource;
  }
}

package io.javaoperatorsdk.operator.baseapi.generickubernetesresourcehandling;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.dependent.generickubernetesresource.GenericKubernetesDependentSpec;
import io.javaoperatorsdk.operator.dependent.generickubernetesresource.GenericKubernetesDependentTestBase;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

@Sample(
    tldr = "Working with GenericKubernetesResource for Dynamic Resource Types",
    description =
        "Demonstrates how to use GenericKubernetesResource to work with Kubernetes resources"
            + " dynamically without requiring compile-time type definitions. This approach is"
            + " useful when building operators that need to manage arbitrary Kubernetes resources"
            + " or when the resource types are not known at compile time. The test shows how to"
            + " handle generic resources as dependent resources in a reconciler.")
public class GenericKubernetesResourceHandlingIT
    extends GenericKubernetesDependentTestBase<GenericKubernetesResourceHandlingCustomResource> {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new GenericKubernetesResourceHandlingReconciler())
          .build();

  @Override
  public LocallyRunOperatorExtension extension() {
    return extension;
  }

  @Override
  public GenericKubernetesResourceHandlingCustomResource testResource(String name, String data) {
    var resource = new GenericKubernetesResourceHandlingCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(name).build());
    resource.setSpec(new GenericKubernetesDependentSpec());
    resource.getSpec().setValue(INITIAL_DATA);
    return resource;
  }
}

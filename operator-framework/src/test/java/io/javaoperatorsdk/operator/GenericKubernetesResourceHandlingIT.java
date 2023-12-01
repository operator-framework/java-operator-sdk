package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.generickubernetesresource.GenericKubernetesDependentSpec;
import io.javaoperatorsdk.operator.sample.generickubernetesresource.generickubernetesresourcehandling.GenericKubernetesResourceHandlingCustomResource;
import io.javaoperatorsdk.operator.sample.generickubernetesresource.generickubernetesresourcehandling.GenericKubernetesResourceHandlingReconciler;

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
  GenericKubernetesResourceHandlingCustomResource testResource(String name, String data) {
    var resource = new GenericKubernetesResourceHandlingCustomResource();
    resource.setMetadata(new ObjectMetaBuilder()
        .withName(name)
        .build());
    resource.setSpec(new GenericKubernetesDependentSpec());
    resource.getSpec().setValue(INITIAL_DATA);
    return resource;
  }

}

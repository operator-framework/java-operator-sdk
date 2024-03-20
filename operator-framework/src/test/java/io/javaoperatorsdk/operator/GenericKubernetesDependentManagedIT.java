package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.generickubernetesresource.GenericKubernetesDependentSpec;
import io.javaoperatorsdk.operator.sample.generickubernetesresource.generickubernetesdependentresourcemanaged.GenericKubernetesDependentManagedCustomResource;
import io.javaoperatorsdk.operator.sample.generickubernetesresource.generickubernetesdependentresourcemanaged.GenericKubernetesDependentManagedReconciler;
import org.junit.jupiter.api.extension.RegisterExtension;

public class GenericKubernetesDependentManagedIT
    extends GenericKubernetesDependentTestBase<GenericKubernetesDependentManagedCustomResource> {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new GenericKubernetesDependentManagedReconciler())
          .build();

  @Override
  public LocallyRunOperatorExtension extension() {
    return extension;
  }

  @Override
  GenericKubernetesDependentManagedCustomResource testResource(String name, String data) {
    var resource = new GenericKubernetesDependentManagedCustomResource();
    resource.setMetadata(new ObjectMetaBuilder()
        .withName(name)
        .build());
    resource.setSpec(new GenericKubernetesDependentSpec());
    resource.getSpec().setValue(INITIAL_DATA);
    return resource;
  }

}

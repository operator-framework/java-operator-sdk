package io.javaoperatorsdk.operator.dependent.generickubernetesresource.generickubernetesdependentresourcemanaged;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.dependent.generickubernetesresource.GenericKubernetesDependentSpec;
import io.javaoperatorsdk.operator.dependent.generickubernetesresource.GenericKubernetesDependentTestBase;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

public class GenericKubernetesDependentManagedIT
    extends GenericKubernetesDependentTestBase<GenericKubernetesDependentManagedCustomResource> {

  @RegisterExtension
  static LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new GenericKubernetesDependentManagedReconciler())
          .build();

  @Override
  public LocallyRunOperatorExtension extension() {
    return extension;
  }

  @Override
  public GenericKubernetesDependentManagedCustomResource testResource(String name, String data) {
    var resource = new GenericKubernetesDependentManagedCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(name).build());
    resource.setSpec(new GenericKubernetesDependentSpec());
    resource.getSpec().setValue(INITIAL_DATA);
    return resource;
  }
}

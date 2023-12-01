package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.generickubernetesdependentresourcemanaged.GenericKubernetesDependentManagedCustomResource;
import io.javaoperatorsdk.operator.sample.generickubernetesdependentresourcemanaged.GenericKubernetesDependentManagedReconciler;
import io.javaoperatorsdk.operator.sample.generickubernetesdependentresourcemanaged.GenericKubernetesDependentSpec;

public class GenericKubernetesDependentManagedIT
    extends GenericKubernetesDependentTestBase<GenericKubernetesDependentManagedCustomResource> {

  public static final String INITIAL_DATA = "Initial data";

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

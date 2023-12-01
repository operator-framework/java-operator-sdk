package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.generickubernetesdependentresourcemanaged.GenericKubernetesDependentManagedCustomResource;
import io.javaoperatorsdk.operator.sample.generickubernetesdependentresourcemanaged.GenericKubernetesDependentManagedReconciler;
import io.javaoperatorsdk.operator.sample.generickubernetesdependentresourcemanaged.GenericKubernetesDependentManagedSpec;
import io.javaoperatorsdk.operator.sample.generickubernetesdependentstandalone.ConfigMapGenericKubernetesDependent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class GenericKubernetesDependentManagedIT {

  public static final String INITIAL_DATA = "Initial data";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new GenericKubernetesDependentManagedReconciler())
          .build();

  @Test
  void testReconcile() {
    operator.create(testResource());

    await().untilAsserted(() -> {
      var cm = operator.get(ConfigMap.class, "test1");
      assertThat(cm).isNotNull();
      assertThat(cm.getData()).containsEntry(ConfigMapGenericKubernetesDependent.KEY, INITIAL_DATA);
    });
  }

  GenericKubernetesDependentManagedCustomResource testResource() {
    var resource = new GenericKubernetesDependentManagedCustomResource();
    resource.setMetadata(new ObjectMetaBuilder()
        .withName("test1")
        .build());
    resource.setSpec(new GenericKubernetesDependentManagedSpec());
    resource.getSpec().setValue(INITIAL_DATA);
    return resource;
  }

}

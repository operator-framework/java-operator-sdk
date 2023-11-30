package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.generickubernetesdependentstandalone.ConfigMapGenericKubernetesDependent;
import io.javaoperatorsdk.operator.sample.generickubernetesdependentstandalone.GenericKubernetesDependentStandaloneCustomResource;
import io.javaoperatorsdk.operator.sample.generickubernetesdependentstandalone.GenericKubernetesDependentStandaloneReconciler;
import io.javaoperatorsdk.operator.sample.generickubernetesdependentstandalone.GenericKubernetesDependentStandaloneSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class GenericKubernetesDependentStandaloneIT {

  public static final String INITIAL_DATA = "Initial data";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withConfigurationService(o -> o.withCloseClientOnStop(false))
          .withReconciler(new GenericKubernetesDependentStandaloneReconciler())
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

  GenericKubernetesDependentStandaloneCustomResource testResource() {
    var resource = new GenericKubernetesDependentStandaloneCustomResource();
    resource.setMetadata(new ObjectMetaBuilder()
        .withName("test1")
        .build());
    resource.setSpec(new GenericKubernetesDependentStandaloneSpec());
    resource.getSpec().setValue(INITIAL_DATA);

    return resource;
  }

  // @Test
  void testInformer() {
    GenericKubernetesResource res = new GenericKubernetesResource();
    res.setApiVersion("v1");
    res.setKind("ConfigMap");

    try (var client = new KubernetesClientBuilder().build()) {
      client.genericKubernetesResources("v1", "ConfigMap").inAnyNamespace().inform()
          .addEventHandler(new ResourceEventHandler<>() {
            @Override
            public void onAdd(GenericKubernetesResource genericKubernetesResource) {
              System.out.println(genericKubernetesResource);
            }

            @Override
            public void onUpdate(GenericKubernetesResource genericKubernetesResource,
                GenericKubernetesResource t1) {
              System.out.println(genericKubernetesResource);
            }

            @Override
            public void onDelete(GenericKubernetesResource genericKubernetesResource, boolean b) {
              System.out.println(genericKubernetesResource);
            }
          });
    }
  }

}

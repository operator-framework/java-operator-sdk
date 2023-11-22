package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.generickubernetesdependentstandalone.GenericKubernetesDependentStandaloneReconciler;

public class GenericKubernetesDependentStandaloneIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withConfigurationService(o -> o.withCloseClientOnStop(false))
          .withReconciler(new GenericKubernetesDependentStandaloneReconciler())
          .build();

  @Test
  void testInformer() {
    GenericKubernetesResource res = new GenericKubernetesResource();
    res.setApiVersion("v1");
    res.setKind("ConfigMap");

    try (var client = new KubernetesClientBuilder().build()) {
      client.resource(res).inform().addEventHandler(new ResourceEventHandler<>() {
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

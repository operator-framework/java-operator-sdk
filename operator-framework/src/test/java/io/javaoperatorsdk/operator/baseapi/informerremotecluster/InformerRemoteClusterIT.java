package io.javaoperatorsdk.operator.baseapi.informerremotecluster;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubeapitest.junit.EnableKubeAPIServer;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

import static io.javaoperatorsdk.operator.baseapi.informerremotecluster.InformerRemoteClusterReconciler.DATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@EnableKubeAPIServer
class InformerRemoteClusterIT {

  public static final String NAME = "test1";
  public static final String CONFIG_MAP_NAME = "testcm";
  public static final String INITIAL_VALUE = "initial_value";
  public static final String CHANGED_VALUE = "changed_value";
  public static final String CM_NAMESPACE = "default";

  // injected by Kube API Test. Client for another cluster.
  static KubernetesClient kubernetesClient;

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new InformerRemoteClusterReconciler(kubernetesClient))
          .build();

  @Test
  void testRemoteClusterInformer() {
    var r = extension.create(testCustomResource());

    var cm = kubernetesClient.configMaps()
        .resource(remoteConfigMap(r.getMetadata().getName(),
            r.getMetadata().getNamespace()))
        .create();

    // config map does not exist on the primary resource cluster
    assertThat(extension.getKubernetesClient().configMaps()
        .inNamespace(CM_NAMESPACE)
        .withName(CONFIG_MAP_NAME).get()).isNull();

    await().untilAsserted(() -> {
      var cr = extension.get(InformerRemoteClusterCustomResource.class, NAME);
      assertThat(cr.getStatus()).isNotNull();
      assertThat(cr.getStatus().getRemoteConfigMapMessage()).isEqualTo(INITIAL_VALUE);
    });

    cm.getData().put(DATA_KEY, CHANGED_VALUE);
    kubernetesClient.configMaps().resource(cm).update();

    await().untilAsserted(() -> {
      var cr = extension.get(InformerRemoteClusterCustomResource.class, NAME);
      assertThat(cr.getStatus().getRemoteConfigMapMessage()).isEqualTo(CHANGED_VALUE);
    });
  }

  InformerRemoteClusterCustomResource testCustomResource() {
    var res = new InformerRemoteClusterCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName(NAME)
        .build());
    return res;
  }

  ConfigMap remoteConfigMap(String ownerName, String ownerNamespace) {
    return new ConfigMapBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withName(CONFIG_MAP_NAME)
            .withNamespace(CM_NAMESPACE)
            .withAnnotations(Map.of(
                Mappers.DEFAULT_ANNOTATION_FOR_NAME, ownerName,
                Mappers.DEFAULT_ANNOTATION_FOR_NAMESPACE, ownerNamespace))
            .build())
        .withData(Map.of(DATA_KEY, INITIAL_VALUE))
        .build();
  }

}

package io.javaoperatorsdk.operator.api.reconciler;

import org.junit.jupiter.api.Test;

import io.fabric8.kubeapitest.junit.EnableKubeAPIServer;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubeAPIServer
class PrimaryUpdateAndCacheUtilsIntegrationTest {

  public static final String DEFAULT_NS = "default";
  public static final String TEST_RESOURCE_NAME = "test1";
  public static final String FINALIZER = "int.test/finalizer";
  static KubernetesClient client;

  @Test
  void testFinalizerAddAndRemoval() {
    var cm =
        client
            .resource(
                new ConfigMapBuilder()
                    .withMetadata(
                        new ObjectMetaBuilder()
                            .withName(TEST_RESOURCE_NAME)
                            .withNamespace(DEFAULT_NS)
                            .build())
                    .build())
            .create();

    PrimaryUpdateAndCacheUtils.addFinalizer(client, cm, FINALIZER);

    cm = client.configMaps().inNamespace(DEFAULT_NS).withName(TEST_RESOURCE_NAME).get();
    assertThat(cm.getMetadata().getFinalizers()).containsExactly(FINALIZER);

    PrimaryUpdateAndCacheUtils.removeFinalizer(client, cm, FINALIZER);

    cm = client.configMaps().inNamespace(DEFAULT_NS).withName(TEST_RESOURCE_NAME).get();
    assertThat(cm.getMetadata().getFinalizers()).isEmpty();
    client.resource(cm).delete();
  }
}

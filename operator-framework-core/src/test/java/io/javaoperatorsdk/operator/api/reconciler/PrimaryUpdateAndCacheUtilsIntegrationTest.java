package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.fabric8.kubeapitest.junit.EnableKubeAPIServer;
import io.fabric8.kubernetes.api.model.ConfigMap;
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
    var cm = createConfigMap();
    PrimaryUpdateAndCacheUtils.addFinalizer(client, cm, FINALIZER);

    cm = getTestConfigMap();
    assertThat(cm.getMetadata().getFinalizers()).containsExactly(FINALIZER);

    PrimaryUpdateAndCacheUtils.removeFinalizer(client, cm, FINALIZER);

    cm = getTestConfigMap();
    assertThat(cm.getMetadata().getFinalizers()).isEmpty();
    client.resource(cm).delete();
  }

  private static ConfigMap getTestConfigMap() {
    return client.configMaps().inNamespace(DEFAULT_NS).withName(TEST_RESOURCE_NAME).get();
  }

  @Test
  void testFinalizerAddRetryOnOptimisticLockFailure() {
    var cm = createConfigMap();
    // update resource, so it has a new version on the server
    cm.setData(Map.of("k", "v"));
    client.resource(cm).update();

    PrimaryUpdateAndCacheUtils.addFinalizer(client, cm, FINALIZER);

    cm = getTestConfigMap();
    assertThat(cm.getMetadata().getFinalizers()).containsExactly(FINALIZER);

    cm.setData(Map.of("k2", "v2"));
    client.resource(cm).update();

    PrimaryUpdateAndCacheUtils.removeFinalizer(client, cm, FINALIZER);
    cm = getTestConfigMap();
    assertThat(cm.getMetadata().getFinalizers()).isEmpty();

    client.resource(cm).delete();
  }

  private static ConfigMap createConfigMap() {
    return client
        .resource(
            new ConfigMapBuilder()
                .withMetadata(
                    new ObjectMetaBuilder()
                        .withName(TEST_RESOURCE_NAME)
                        .withNamespace(DEFAULT_NS)
                        .build())
                .build())
        .create();
  }
}

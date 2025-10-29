/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

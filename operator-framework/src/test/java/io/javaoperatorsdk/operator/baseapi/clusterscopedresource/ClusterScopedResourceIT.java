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
package io.javaoperatorsdk.operator.baseapi.clusterscopedresource;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.IntegrationTestConstants.GARBAGE_COLLECTION_TIMEOUT_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ClusterScopedResourceIT {

  public static final String TEST_NAME = "test1";
  public static final String INITIAL_DATA = "initialData";
  public static final String UPDATED_DATA = "updatedData";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new ClusterScopedCustomResourceReconciler())
          .build();

  @Test
  void crudOperationOnClusterScopedCustomResource() {
    var resource = operator.create(testResource());

    await()
        .untilAsserted(
            () -> {
              var res = operator.get(ClusterScopedCustomResource.class, TEST_NAME);
              assertThat(res.getStatus()).isNotNull();
              assertThat(res.getStatus().getCreated()).isTrue();
              var cm = operator.get(ConfigMap.class, TEST_NAME);
              assertThat(cm).isNotNull();
              assertThat(cm.getData().get(ClusterScopedCustomResourceReconciler.DATA_KEY))
                  .isEqualTo(INITIAL_DATA);
            });

    resource.getSpec().setData(UPDATED_DATA);
    operator.replace(resource);
    await()
        .untilAsserted(
            () -> {
              var cm = operator.get(ConfigMap.class, TEST_NAME);
              assertThat(cm).isNotNull();
              assertThat(cm.getData().get(ClusterScopedCustomResourceReconciler.DATA_KEY))
                  .isEqualTo(UPDATED_DATA);
            });

    operator.delete(resource);
    await()
        .atMost(Duration.ofSeconds(GARBAGE_COLLECTION_TIMEOUT_SECONDS))
        .untilAsserted(() -> assertThat(operator.get(ConfigMap.class, TEST_NAME)).isNull());
  }

  ClusterScopedCustomResource testResource() {
    var res = new ClusterScopedCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_NAME).build());
    res.setSpec(new ClusterScopedCustomResourceSpec());
    res.getSpec().setTargetNamespace(operator.getNamespace());
    res.getSpec().setData(INITIAL_DATA);

    return res;
  }
}

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
package io.javaoperatorsdk.operator.processing.event.source.cache;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;

import static org.assertj.core.api.Assertions.assertThat;

class KubernetesResourceFetcherTest {

  public static final String DEFAULT_NAMESPACE = "default";
  public static final String TEST_RESOURCE_NAME = "test1";

  @Test
  void inverseKeyFunction() {
    String key = BoundedItemStore.namespaceKeyFunc().apply(namespacedResource());
    var resourceID = KubernetesResourceFetcher.inverseNamespaceKeyFunction().apply(key);

    assertThat(resourceID.getNamespace()).isPresent().get().isEqualTo(DEFAULT_NAMESPACE);
    assertThat(resourceID.getName()).isEqualTo(TEST_RESOURCE_NAME);

    key = BoundedItemStore.namespaceKeyFunc().apply(clusterScopedResource());
    resourceID = KubernetesResourceFetcher.inverseNamespaceKeyFunction().apply(key);

    assertThat(resourceID.getNamespace()).isEmpty();
    assertThat(resourceID.getName()).isEqualTo(TEST_RESOURCE_NAME);
  }

  private HasMetadata namespacedResource() {
    var cm = new ConfigMap();
    cm.setMetadata(
        new ObjectMetaBuilder()
            .withName(TEST_RESOURCE_NAME)
            .withNamespace(DEFAULT_NAMESPACE)
            .build());
    return cm;
  }

  private HasMetadata clusterScopedResource() {
    var cm = new CustomResourceDefinition();
    cm.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    return cm;
  }
}

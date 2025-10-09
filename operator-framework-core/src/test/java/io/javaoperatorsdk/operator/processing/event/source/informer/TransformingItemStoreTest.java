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
package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;

import static io.fabric8.kubernetes.client.informers.cache.Cache.metaNamespaceKeyFunc;
import static org.assertj.core.api.Assertions.assertThat;

class TransformingItemStoreTest {

  @Test
  void cachedObjectTransformed() {
    TransformingItemStore<ConfigMap> transformingItemStore =
        new TransformingItemStore<>(
            r -> {
              r.getMetadata().setLabels(null);
              return r;
            });

    var cm = configMap();
    cm.getMetadata().setLabels(Map.of("k", "v"));
    transformingItemStore.put(metaNamespaceKeyFunc(cm), cm);

    assertThat(transformingItemStore.get(metaNamespaceKeyFunc(cm)).getMetadata().getLabels())
        .isNull();
  }

  @Test
  void preservesSelectedAttributes() {
    TransformingItemStore<ConfigMap> transformingItemStore =
        new TransformingItemStore<>(
            r -> {
              r.getMetadata().setName(null);
              r.getMetadata().setNamespace(null);
              r.getMetadata().setResourceVersion(null);
              return r;
            });
    var cm = configMap();
    transformingItemStore.put(metaNamespaceKeyFunc(cm), cm);

    assertThat(transformingItemStore.get(metaNamespaceKeyFunc(cm)).getMetadata().getName())
        .isNotNull();
    assertThat(transformingItemStore.get(metaNamespaceKeyFunc(cm)).getMetadata().getNamespace())
        .isNotNull();
    assertThat(
            transformingItemStore.get(metaNamespaceKeyFunc(cm)).getMetadata().getResourceVersion())
        .isNotNull();
  }

  ConfigMap configMap() {
    var cm = new ConfigMap();
    cm.setMetadata(
        new ObjectMetaBuilder()
            .withName("test1")
            .withNamespace("default")
            .withResourceVersion("1")
            .build());
    return cm;
  }
}

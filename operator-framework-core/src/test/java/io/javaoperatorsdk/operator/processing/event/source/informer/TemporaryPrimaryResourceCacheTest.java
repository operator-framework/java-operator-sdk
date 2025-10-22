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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import static org.assertj.core.api.Assertions.assertThat;

class TemporaryPrimaryResourceCacheTest {

  public static final String RESOURCE_VERSION = "2";

  private TemporaryResourceCache<ConfigMap> temporaryResourceCache;

  @BeforeEach
  void setup() {
    temporaryResourceCache = new TemporaryResourceCache<>(true);
  }

  @Test
  void updateAddsTheResourceIntoCacheIfTheInformerHasThePreviousResourceVersion() {
    var testResource = testResource();
    var prevTestResource = testResource();
    prevTestResource.getMetadata().setResourceVersion("1");

    temporaryResourceCache.putResource(testResource);

    var cached = temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource));
    assertThat(cached).isPresent();
  }

  @Test
  void updateNotAddsTheResourceIntoCacheIfLaterVersionKnown() {
    var testResource = testResource();

    temporaryResourceCache.onAddOrUpdateEvent(
        testResource.toBuilder().editMetadata().withResourceVersion("3").endMetadata().build());

    temporaryResourceCache.putResource(testResource);

    var cached = temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource));
    assertThat(cached).isNotPresent();
  }

  @Test
  void addOperationAddsTheResourceIfInformerCacheStillEmpty() {
    var testResource = testResource();

    temporaryResourceCache.putResource(testResource);

    var cached = temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource));
    assertThat(cached).isPresent();
  }

  @Test
  void addOperationNotAddsTheResourceIfInformerCacheNotEmpty() {
    var testResource = testResource();

    temporaryResourceCache.putResource(testResource);

    temporaryResourceCache.putResource(
        new ConfigMapBuilder(testResource)
            .editMetadata()
            .withResourceVersion("1")
            .endMetadata()
            .build());

    var cached = temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource));
    assertThat(cached.orElseThrow().getMetadata().getResourceVersion()).isEqualTo(RESOURCE_VERSION);
  }

  @Test
  void removesResourceFromCache() {
    ConfigMap testResource = propagateTestResourceToCache();

    temporaryResourceCache.onAddOrUpdateEvent(
        new ConfigMapBuilder(testResource)
            .editMetadata()
            .withResourceVersion("3")
            .endMetadata()
            .build());

    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isNotPresent();
  }

  @Test
  void resourceNoVersionParsing() {
    this.temporaryResourceCache = new TemporaryResourceCache<>(false);

    this.temporaryResourceCache.putResource(testResource());

    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource())))
        .isEmpty();
  }

  @Test
  void lockedEventBeforePut() throws Exception {
    var testResource = testResource();

    temporaryResourceCache.startModifying(ResourceID.fromResource(testResource));

    try (ExecutorService ex = Executors.newSingleThreadExecutor()) {
      var result = ex.submit(() -> temporaryResourceCache.onAddOrUpdateEvent(testResource));

      temporaryResourceCache.putResource(testResource);
      assertThat(result.isDone()).isFalse();
      temporaryResourceCache.doneModifying(ResourceID.fromResource(testResource));
      assertThat(result.get()).isTrue();
    }
  }

  @Test
  void putBeforeEvent() {
    var testResource = testResource();

    // first ensure an event is not known
    var result = temporaryResourceCache.onAddOrUpdateEvent(testResource);
    assertThat(result).isFalse();

    var nextResource = testResource();
    nextResource.getMetadata().setResourceVersion("3");
    temporaryResourceCache.putResource(nextResource);

    // now expect an event with the matching resourceVersion to be known after the put
    result = temporaryResourceCache.onAddOrUpdateEvent(nextResource);
    assertThat(result).isTrue();
  }

  @Test
  void rapidDeletion() {
    var testResource = testResource();

    temporaryResourceCache.onAddOrUpdateEvent(testResource);
    temporaryResourceCache.onDeleteEvent(
        new ConfigMapBuilder(testResource)
            .editMetadata()
            .withResourceVersion("3")
            .endMetadata()
            .build(),
        false);
    temporaryResourceCache.putResource(testResource);

    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isEmpty();
  }

  private ConfigMap propagateTestResourceToCache() {
    var testResource = testResource();
    temporaryResourceCache.putResource(testResource);
    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isPresent();
    return testResource;
  }

  ConfigMap testResource() {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMetaBuilder().withLabels(Map.of("k", "v")).build());
    configMap.getMetadata().setName("test");
    configMap.getMetadata().setNamespace("default");
    configMap.getMetadata().setResourceVersion(RESOURCE_VERSION);
    configMap.getMetadata().setUid("test-uid");
    return configMap;
  }
}

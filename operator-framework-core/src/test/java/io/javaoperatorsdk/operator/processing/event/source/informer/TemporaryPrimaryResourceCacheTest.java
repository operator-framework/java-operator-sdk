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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.TemporaryResourceCache.ExpirationCache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TemporaryPrimaryResourceCacheTest {

  public static final String RESOURCE_VERSION = "2";

  @SuppressWarnings("unchecked")
  private InformerEventSource<ConfigMap, ?> informerEventSource;

  private TemporaryResourceCache<ConfigMap> temporaryResourceCache;

  @BeforeEach
  void setup() {
    informerEventSource = mock(InformerEventSource.class);
    temporaryResourceCache = new TemporaryResourceCache<>(informerEventSource);
  }

  @Test
  void updateAddsTheResourceIntoCacheIfTheInformerHasThePreviousResourceVersion() {
    var testResource = testResource();
    var prevTestResource = testResource();
    prevTestResource.getMetadata().setResourceVersion("0");
    when(informerEventSource.get(any())).thenReturn(Optional.of(prevTestResource));

    temporaryResourceCache.putResource(testResource, "0");

    var cached = temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource));
    assertThat(cached).isPresent();
  }

  @Test
  void updateNotAddsTheResourceIntoCacheIfTheInformerHasOtherVersion() {
    var testResource = testResource();
    var informerCachedResource = testResource();
    informerCachedResource.getMetadata().setResourceVersion("2");
    when(informerEventSource.get(any())).thenReturn(Optional.of(informerCachedResource));

    temporaryResourceCache.putResource(testResource, "0");

    var cached = temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource));
    assertThat(cached).isNotPresent();
  }

  @Test
  void addOperationAddsTheResourceIfInformerCacheStillEmpty() {
    var testResource = testResource();
    when(informerEventSource.get(any())).thenReturn(Optional.empty());

    temporaryResourceCache.putAddedResource(testResource);

    var cached = temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource));
    assertThat(cached).isPresent();
  }

  @Test
  void addOperationNotAddsTheResourceIfInformerCacheNotEmpty() {
    var testResource = testResource();
    when(informerEventSource.get(any())).thenReturn(Optional.of(testResource()));

    temporaryResourceCache.putAddedResource(testResource);

    var cached = temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource));
    assertThat(cached).isNotPresent();
  }

  @Test
  void removesResourceFromCache() {
    ConfigMap testResource = propagateTestResourceToCache();

    temporaryResourceCache.onAddOrUpdateEvent(testResource());

    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isNotPresent();
  }

  @Test
  void resourceVersionParsing() {
    this.temporaryResourceCache = new TemporaryResourceCache<>(informerEventSource);

    ConfigMap testResource = propagateTestResourceToCache();

    // an event with a newer version will not remove
    temporaryResourceCache.onAddOrUpdateEvent(
        new ConfigMapBuilder(testResource)
            .editMetadata()
            .withResourceVersion("1")
            .endMetadata()
            .build());

    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isPresent();

    // anything else will remove
    temporaryResourceCache.onAddOrUpdateEvent(testResource());

    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isNotPresent();
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
    temporaryResourceCache.putAddedResource(testResource);

    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isEmpty();
  }

  @Test
  void expirationCacheMax() {
    ExpirationCache<Integer> cache = new ExpirationCache<>(2, Integer.MAX_VALUE);

    cache.add(1);
    cache.add(2);
    cache.add(3);

    assertThat(cache.contains(1)).isFalse();
    assertThat(cache.contains(2)).isTrue();
    assertThat(cache.contains(3)).isTrue();
  }

  @Test
  void expirationCacheTtl() {
    ExpirationCache<Integer> cache = new ExpirationCache<>(2, 1);

    cache.add(1);
    cache.add(2);

    Awaitility.await()
        .atMost(1, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              assertThat(cache.contains(1)).isFalse();
              assertThat(cache.contains(2)).isFalse();
            });
  }

  private ConfigMap propagateTestResourceToCache() {
    var testResource = testResource();
    when(informerEventSource.get(any())).thenReturn(Optional.empty());
    temporaryResourceCache.putAddedResource(testResource);
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

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class TemporaryResourceCacheTest {

  public static final String RESOURCE_VERSION = "2";

  private TemporaryResourceCache<ConfigMap> temporaryResourceCache;
  private volatile String latestSyncVersion;
  private ManagedInformerEventSource managedInformerEventSource =
      mock(ManagedInformerEventSource.class);

  @BeforeEach
  void setup() {
    latestSyncVersion = "1";
    managedInformerEventSource = mock(ManagedInformerEventSource.class);
    var mim = mock(InformerManager.class);
    when(managedInformerEventSource.manager()).thenReturn(mim);
    when(mim.isWatchingNamespace(any())).thenReturn(true);
    when(mim.lastSyncResourceVersion(any())).then(a -> latestSyncVersion);
    temporaryResourceCache = new TemporaryResourceCache<>(true, managedInformerEventSource);
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
        ResourceAction.ADDED,
        testResource.toBuilder().editMetadata().withResourceVersion("3").endMetadata().build(),
        null);
    latestSyncVersion = "3";
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
        ResourceAction.ADDED,
        new ConfigMapBuilder(testResource)
            .editMetadata()
            .withResourceVersion("3")
            .endMetadata()
            .build(),
        null);
    latestSyncVersion = "3";
    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isNotPresent();
  }

  @Test
  void nonComparableResourceVersionsDisables() {
    this.temporaryResourceCache =
        new TemporaryResourceCache<>(false, mock(ManagedInformerEventSource.class));

    this.temporaryResourceCache.putResource(testResource());

    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource())))
        .isEmpty();
  }

  @Test
  void rapidDeletion() {
    var testResource = testResource();

    temporaryResourceCache.onAddOrUpdateEvent(ResourceAction.ADDED, testResource, null);
    temporaryResourceCache.onDeleteEvent(
        new ConfigMapBuilder(testResource)
            .editMetadata()
            .withResourceVersion("3")
            .endMetadata()
            .build(),
        false);
    latestSyncVersion = "3";
    temporaryResourceCache.putResource(testResource);

    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isEmpty();
  }

  @Test
  void removalOfGhostResources() {
    var tr = testResource();
    this.temporaryResourceCache.putResource(tr);

    // ghost check should not remove when latestSyncVersion is not ahead
    temporaryResourceCache.checkGhostResources();
    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(tr)))
        .isPresent();

    latestSyncVersion = "3";

    temporaryResourceCache.checkGhostResources();
    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(tr))).isEmpty();
    verify(managedInformerEventSource, times(1))
        .handleEvent(eq(ResourceAction.DELETED), eq(tr), isNull(), eq(true));
  }

  @Test
  void ghostResourceIsNotRemovedIfLatestSyncVersionIsOlder() {
    this.temporaryResourceCache.putResource(testResource());
    latestSyncVersion = "1";

    temporaryResourceCache.checkGhostResources();
    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource())))
        .isPresent();
  }

  @Test
  void ghostRemovalRemovesResourcesOnNotFollowedNamespaces() {
    var tr = testResource();
    temporaryResourceCache.putResource(tr);

    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(tr)))
        .isPresent();

    // simulate namespace no longer being watched
    var mim = managedInformerEventSource.manager();
    when(mim.isWatchingNamespace(tr.getMetadata().getNamespace())).thenReturn(false);

    temporaryResourceCache.checkGhostResources();
    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(tr))).isEmpty();

    // no delete event should be fired for resources removed due to namespace change
    verify(managedInformerEventSource, times(0))
        .handleEvent(any(), any(), any(), any(Boolean.class));
  }

  @Test
  void doNotCacheResourceOnPutIfNamespaceIsNotFollowedAnymore() {
    var mim = managedInformerEventSource.manager();
    when(mim.isWatchingNamespace("default")).thenReturn(false);

    var tr = testResource();
    temporaryResourceCache.putResource(tr);

    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(tr))).isEmpty();
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

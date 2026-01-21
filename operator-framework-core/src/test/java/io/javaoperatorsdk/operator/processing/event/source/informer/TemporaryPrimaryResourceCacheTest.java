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
import io.javaoperatorsdk.operator.processing.event.source.informer.TemporaryResourceCache.EventHandling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        ResourceAction.ADDED,
        testResource.toBuilder().editMetadata().withResourceVersion("3").endMetadata().build(),
        null);

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

    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isNotPresent();
  }

  @Test
  void nonComparableResourceVersionsDisables() {
    this.temporaryResourceCache = new TemporaryResourceCache<>(false);

    this.temporaryResourceCache.putResource(testResource());

    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource())))
        .isEmpty();
  }

  @Test
  void eventReceivedDuringFiltering() throws Exception {
    var testResource = testResource();

    temporaryResourceCache.startEventFilteringModify(ResourceID.fromResource(testResource));

    temporaryResourceCache.putResource(testResource);
    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isPresent();

    temporaryResourceCache.onAddOrUpdateEvent(ResourceAction.ADDED, testResource, null);
    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isEmpty();

    var doneRes =
        temporaryResourceCache.doneEventFilterModify(ResourceID.fromResource(testResource), "2");

    assertThat(doneRes).isEmpty();
    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isEmpty();
  }

  @Test
  void newerEventDuringFiltering() {
    var testResource = testResource();

    temporaryResourceCache.startEventFilteringModify(ResourceID.fromResource(testResource));

    temporaryResourceCache.putResource(testResource);
    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isPresent();

    var testResource2 = testResource();
    testResource2.getMetadata().setResourceVersion("3");
    temporaryResourceCache.onAddOrUpdateEvent(ResourceAction.UPDATED, testResource2, testResource);
    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isEmpty();

    var doneRes =
        temporaryResourceCache.doneEventFilterModify(ResourceID.fromResource(testResource), "2");

    assertThat(doneRes).isPresent();
    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isEmpty();
  }

  @Test
  void eventAfterFiltering() {
    var testResource = testResource();

    temporaryResourceCache.startEventFilteringModify(ResourceID.fromResource(testResource));

    temporaryResourceCache.putResource(testResource);
    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isPresent();

    var doneRes =
        temporaryResourceCache.doneEventFilterModify(ResourceID.fromResource(testResource), "2");

    assertThat(doneRes).isEmpty();
    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isPresent();

    temporaryResourceCache.onAddOrUpdateEvent(ResourceAction.ADDED, testResource, null);
    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isEmpty();
  }

  @Test
  void putBeforeEvent() {
    var testResource = testResource();

    // first ensure an event is not known
    var result =
        temporaryResourceCache.onAddOrUpdateEvent(ResourceAction.ADDED, testResource, null);
    assertThat(result).isEqualTo(EventHandling.NEW);

    var nextResource = testResource();
    nextResource.getMetadata().setResourceVersion("3");
    temporaryResourceCache.putResource(nextResource);

    // the result is obsolete
    result = temporaryResourceCache.onAddOrUpdateEvent(ResourceAction.UPDATED, nextResource, null);
    assertThat(result).isEqualTo(EventHandling.OBSOLETE);
  }

  @Test
  void putBeforeEventWithEventFiltering() {
    var testResource = testResource();

    // first ensure an event is not known
    var result =
        temporaryResourceCache.onAddOrUpdateEvent(ResourceAction.ADDED, testResource, null);
    assertThat(result).isEqualTo(EventHandling.NEW);

    var nextResource = testResource();
    nextResource.getMetadata().setResourceVersion("3");
    var resourceId = ResourceID.fromResource(testResource);

    temporaryResourceCache.startEventFilteringModify(resourceId);
    temporaryResourceCache.putResource(nextResource);
    temporaryResourceCache.doneEventFilterModify(resourceId, "3");

    // the result is obsolete
    result = temporaryResourceCache.onAddOrUpdateEvent(ResourceAction.UPDATED, nextResource, null);
    assertThat(result).isEqualTo(EventHandling.OBSOLETE);
  }

  @Test
  void putAfterEventWithEventFilteringNoPost() {
    var testResource = testResource();

    // first ensure an event is not known
    var result =
        temporaryResourceCache.onAddOrUpdateEvent(ResourceAction.ADDED, testResource, null);
    assertThat(result).isEqualTo(EventHandling.NEW);

    var nextResource = testResource();
    nextResource.getMetadata().setResourceVersion("3");
    var resourceId = ResourceID.fromResource(testResource);

    temporaryResourceCache.startEventFilteringModify(resourceId);
    result =
        temporaryResourceCache.onAddOrUpdateEvent(
            ResourceAction.UPDATED, nextResource, testResource);
    // the result is deferred
    assertThat(result).isEqualTo(EventHandling.DEFER);
    temporaryResourceCache.putResource(nextResource);
    var postEvent = temporaryResourceCache.doneEventFilterModify(resourceId, "3");

    // there is no post event because the done call claimed responsibility for rv 3
    assertTrue(postEvent.isEmpty());
  }

  @Test
  void putAfterEventWithEventFilteringWithPost() {
    var testResource = testResource();
    var resourceId = ResourceID.fromResource(testResource);
    temporaryResourceCache.startEventFilteringModify(resourceId);

    // this should be a corner case - watch had a hard reset since the start of the
    // of the update operation, such that 4 rv event is seen prior to the update
    // completing with the 3 rv.
    var nextResource = testResource();
    nextResource.getMetadata().setResourceVersion("4");
    var result =
        temporaryResourceCache.onAddOrUpdateEvent(ResourceAction.ADDED, nextResource, null);
    assertThat(result).isEqualTo(EventHandling.DEFER);

    var postEvent = temporaryResourceCache.doneEventFilterModify(resourceId, "3");

    assertTrue(postEvent.isPresent());
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

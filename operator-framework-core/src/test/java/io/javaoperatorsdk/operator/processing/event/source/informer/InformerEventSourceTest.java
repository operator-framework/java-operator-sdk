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

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.InformerStoppedHandler;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.Cache;
import io.javaoperatorsdk.operator.processing.event.source.EventFilterTestUtils;
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;
import static io.javaoperatorsdk.operator.processing.event.source.EventFilterTestUtils.withResourceVersion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class InformerEventSourceTest {

  private static final String PREV_RESOURCE_VERSION = "0";
  private static final String DEFAULT_RESOURCE_VERSION = "2";

  private InformerEventSource<Deployment, TestCustomResource> informerEventSource;
  private final KubernetesClient clientMock = MockKubernetesClient.client(Deployment.class);
  private TemporaryResourceCache<Deployment> temporaryResourceCache =
      mock(TemporaryResourceCache.class);
  private final EventHandler eventHandlerMock = mock(EventHandler.class);
  private final InformerEventSourceConfiguration<Deployment> informerEventSourceConfiguration =
      mock(InformerEventSourceConfiguration.class);

  @BeforeEach
  void setup() {
    final var informerConfig = mock(InformerConfiguration.class);
    SecondaryToPrimaryMapper secondaryToPrimaryMapper = mock(SecondaryToPrimaryMapper.class);
    when(informerEventSourceConfiguration.getSecondaryToPrimaryMapper())
        .thenReturn(secondaryToPrimaryMapper);
    when(secondaryToPrimaryMapper.toPrimaryResourceIDs(any(), any()))
        .thenReturn(Set.of(ResourceID.fromResource(testDeployment())));
    when(informerEventSourceConfiguration.getInformerConfig()).thenReturn(informerConfig);

    when(informerEventSourceConfiguration.getResourceClass()).thenReturn(Deployment.class);
    when(informerConfig.isComparableResourceVersions()).thenReturn(true);
    when(informerConfig.getEffectiveNamespaces(any())).thenReturn(DEFAULT_NAMESPACES_SET);

    informerEventSource =
        spy(
            new InformerEventSource<>(informerEventSourceConfiguration, clientMock) {
              // mocking start
              @Override
              public synchronized void start() {}
            });

    var mockControllerConfig = mock(ControllerConfiguration.class);
    when(mockControllerConfig.getConfigurationService()).thenReturn(new BaseConfigurationService());

    informerEventSource.setEventHandler(eventHandlerMock);
    informerEventSource.setControllerConfiguration(mockControllerConfig);
    informerEventSource.start();
    informerEventSource.setTemporalResourceCache(temporaryResourceCache);
  }

  @Test
  void propagatesEventAndEvictsTempCacheOnVersionMismatch() {
    withRealTemporaryResourceCache();

    Deployment cachedDeployment = testDeployment();
    cachedDeployment.getMetadata().setResourceVersion(PREV_RESOURCE_VERSION);
    temporaryResourceCache.putResource(cachedDeployment);

    informerEventSource.onUpdate(cachedDeployment, testDeployment());

    verify(eventHandlerMock, times(1)).handleEvent(any());
    verify(temporaryResourceCache, times(1)).onAddOrUpdateEvent(any(), eq(testDeployment()), any());
  }

  @Test
  void genericFilterRejectsAddUpdateAndDelete() {
    informerEventSource.setGenericFilter(r -> false);
    when(temporaryResourceCache.getResourceFromCache(any())).thenReturn(Optional.empty());

    informerEventSource.onAdd(testDeployment());
    informerEventSource.onUpdate(testDeployment(), testDeployment());
    informerEventSource.onDelete(testDeployment(), true);

    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void onAddFilterRejectsAdd() {
    informerEventSource.setOnAddFilter(r -> false);
    when(temporaryResourceCache.getResourceFromCache(any())).thenReturn(Optional.empty());

    informerEventSource.onAdd(testDeployment());

    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void onUpdateFilterRejectsUpdate() {
    informerEventSource.setOnUpdateFilter((r1, r2) -> false);
    when(temporaryResourceCache.getResourceFromCache(any())).thenReturn(Optional.empty());

    informerEventSource.onUpdate(testDeployment(), testDeployment());

    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void onDeleteFilterRejectsDelete() {
    informerEventSource.setOnDeleteFilter((r, b) -> false);
    when(temporaryResourceCache.getResourceFromCache(any())).thenReturn(Optional.empty());

    informerEventSource.onDelete(testDeployment(), true);

    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void deletePropagatesWhenTempCacheEmitsDelete() {
    var resource = testDeployment();
    when(temporaryResourceCache.onDeleteEvent(resource, false))
        .thenReturn(
            Optional.of(new ExtendedResourceEvent(ResourceAction.DELETED, resource, null, false)));

    informerEventSource.onDelete(resource, false);

    verify(eventHandlerMock, times(1)).handleEvent(any());
  }

  @Test
  void deleteSwallowsWhenTempCacheReturnsEmpty() {
    var resource = testDeployment();
    when(temporaryResourceCache.onDeleteEvent(resource, false)).thenReturn(Optional.empty());

    informerEventSource.onDelete(resource, false);

    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void failingUpdate_propagatesEventReceivedDuringWindow() {
    // Filter window opens, an event arrives, the update method throws. The event must
    // still surface as a synthesized propagation.
    withRealTemporaryResourceCache();

    CountDownLatch latch = sendForExceptionThrowingUpdate();
    informerEventSource.onUpdate(
        deploymentWithResourceVersion(1), deploymentWithResourceVersion(2));
    latch.countDown();

    expectHandleUpdateEvent(2, 1);
  }

  @Test
  void failingUpdate_doesNotPopulateTempCache() {
    // putResource is only called from handleRecentResourceUpdate, which never runs when
    // updateMethod throws. The temp cache must therefore stay empty.
    withRealTemporaryResourceCache();

    CountDownLatch latch = sendForExceptionThrowingUpdate();
    informerEventSource.onUpdate(
        deploymentWithResourceVersion(1), deploymentWithResourceVersion(2));
    latch.countDown();

    expectHandleUpdateEvent(2, 1);
    assertThat(temporaryResourceCache.getResources()).isEmpty();
  }

  @Test
  void eventReceivedAfterFailedUpdate_isPropagatedNormally() {
    // After the exception unwinds and the filter window closes, subsequent events must
    // propagate via the regular non-filtered path.
    withRealTemporaryResourceCache();

    CountDownLatch latch = sendForExceptionThrowingUpdate();
    latch.countDown();
    var deployment = deploymentWithResourceVersion(2);

    informerEventSource.onUpdate(deploymentWithResourceVersion(1), deployment);

    expectPropagateEvent(deployment);
  }

  @Test
  void ownUpdateEventIsDeferredDuringActiveFilter() {
    // Sanity check that the InformerEventSource end-to-end pipeline (informer → temp cache
    // → filter support → propagateEvent) suppresses an event for our own write that arrives
    // before the filter closes. Detail-level cases live in EventFilterWindowTest /
    // EventFilterSupportTest.
    withRealTemporaryResourceCache();

    CountDownLatch latch = sendForEventFilteringUpdate(2);
    informerEventSource.onUpdate(
        deploymentWithResourceVersion(1), deploymentWithResourceVersion(2));
    latch.countDown();

    assertNoEventProduced();
  }

  @Test
  void informerStoppedHandlerShouldBeCalledWhenInformerStops() {
    final var exception = new RuntimeException("Informer stopped exceptionally!");
    final var informerStoppedHandler = mock(InformerStoppedHandler.class);
    var configuration =
        ConfigurationService.newOverriddenConfigurationService(
            new BaseConfigurationService(),
            o -> o.withInformerStoppedHandler(informerStoppedHandler));

    var mockControllerConfig = mock(ControllerConfiguration.class);
    when(mockControllerConfig.getConfigurationService()).thenReturn(configuration);

    informerEventSource =
        new InformerEventSource<>(
            informerEventSourceConfiguration,
            MockKubernetesClient.client(
                Deployment.class,
                unused -> {
                  throw exception;
                }));
    informerEventSource.setControllerConfiguration(mockControllerConfig);

    // by default informer fails to start if there is an exception in the client on start.
    // Throws the exception further.
    assertThrows(OperatorException.class, () -> informerEventSource.start());
    verify(informerStoppedHandler, atLeastOnce()).onStop(any(), eq(exception));
  }

  @Test
  void listReplacesResourceFromTempCache() {
    var original = testDeployment();
    var newer = testDeployment();
    newer.getMetadata().setResourceVersion("5");

    when(temporaryResourceCache.getResources())
        .thenReturn(new HashMap<>(Map.of(ResourceID.fromResource(original), newer)));

    var mim = mock(InformerManager.class);
    when(mim.list(nullable(String.class))).thenReturn(Stream.of(original));
    when(informerEventSource.manager()).thenReturn(mim);

    var result = informerEventSource.list(null, Cache.TRUE).toList();

    assertThat(result).containsExactly(newer);
  }

  @Test
  void listExcludesResourceWhenTempCacheContainsNewerVersionThatNoLongerMatchesPredicate() {
    var original = testDeployment();
    original.getMetadata().setResourceVersion("4");
    var newer = testDeployment();
    newer.getMetadata().setResourceVersion("5");

    when(temporaryResourceCache.getResources())
        .thenReturn(new HashMap<>(Map.of(ResourceID.fromResource(original), newer)));

    var mim = mock(InformerManager.class);
    when(mim.list(nullable(String.class), any())).thenReturn(Stream.of(original));
    when(informerEventSource.manager()).thenReturn(mim);

    var result =
        informerEventSource
            .list(null, r -> !"5".equals(r.getMetadata().getResourceVersion()))
            .toList();

    assertThat(result).isEmpty();
  }

  @Test
  void listKeepsResourceWhenNotInTempCache() {
    var original = testDeployment();

    when(temporaryResourceCache.getResources()).thenReturn(new HashMap<>());

    var mim = mock(InformerManager.class);
    when(mim.list(nullable(String.class))).thenReturn(Stream.of(original));
    when(informerEventSource.manager()).thenReturn(mim);

    var result = informerEventSource.list(null, r -> true).toList();

    assertThat(result).containsExactly(original);
  }

  @Test
  void listKeepsResourceWhenTempCacheHasOlderVersion() {
    var original = testDeployment();
    original.getMetadata().setResourceVersion("5");
    var olderTemp = testDeployment();
    olderTemp.getMetadata().setResourceVersion("3");

    when(temporaryResourceCache.getResources())
        .thenReturn(new HashMap<>(Map.of(ResourceID.fromResource(original), olderTemp)));

    var mim = mock(InformerManager.class);
    when(mim.list(nullable(String.class))).thenReturn(Stream.of(original));
    when(informerEventSource.manager()).thenReturn(mim);

    var result = informerEventSource.list(null, r -> true).toList();

    assertThat(result).containsExactly(original);
  }

  @Test
  void listAddsGhostResources() {
    var resource = testDeployment();
    var ghostResource = testDeployment();
    ghostResource.getMetadata().setName("ghost");

    when(temporaryResourceCache.getResources())
        .thenReturn(new HashMap<>(Map.of(ResourceID.fromResource(ghostResource), ghostResource)));

    var mim = mock(InformerManager.class);
    when(mim.list(nullable(String.class))).thenReturn(Stream.of(resource));
    when(informerEventSource.manager()).thenReturn(mim);

    var result = informerEventSource.list(null, r -> true).toList();

    assertThat(result).containsExactlyInAnyOrder(resource, ghostResource);
  }

  @Test
  void byIndexStreamReplacesFromTempCache() {
    var original = testDeployment();
    var newer = testDeployment();
    newer.getMetadata().setResourceVersion("5");

    when(temporaryResourceCache.getResources())
        .thenReturn(new HashMap<>(Map.of(ResourceID.fromResource(original), newer)));

    var informerManager = mock(InformerManager.class);
    when(informerManager.byIndexStream(any(), any())).thenReturn(Stream.of(original));
    when(informerEventSource.manager()).thenReturn(informerManager);
    informerEventSource.addIndexers(Map.of("idx", d -> List.of("key")));

    var result = informerEventSource.byIndexStream("idx", "key").toList();

    assertThat(result).containsExactly(newer);
  }

  @Test
  void byIndexStreamSkipsNewerTempCacheResourceWhenIndexedValueChanged() {
    var original = testDeployment();
    original.getMetadata().setLabels(Map.of("app", "key"));
    var newer = testDeployment();
    newer.getMetadata().setResourceVersion("5");
    newer.getMetadata().setLabels(Map.of("app", "other"));

    when(temporaryResourceCache.getResources())
        .thenReturn(new HashMap<>(Map.of(ResourceID.fromResource(original), newer)));

    var informerManager = mock(InformerManager.class);
    when(informerManager.byIndexStream(any(), any())).thenReturn(Stream.of(original));
    when(informerEventSource.manager()).thenReturn(informerManager);
    informerEventSource.addIndexers(
        Map.of("idx", d -> List.of(d.getMetadata().getLabels().get("app"))));

    var result = informerEventSource.byIndexStream("idx", "key").toList();

    assertThat(result).isEmpty();
  }

  @Test
  void keysIncludeGhostResourceKeys() {
    var resource = testDeployment();
    var ghostResource = testDeployment();
    ghostResource.getMetadata().setName("ghost");

    var resourceId = ResourceID.fromResource(resource);
    var ghostResourceId = ResourceID.fromResource(ghostResource);

    when(temporaryResourceCache.getResources()).thenReturn(Map.of(ghostResourceId, ghostResource));
    when(temporaryResourceCache.isEmpty()).thenReturn(false);

    var mim = mock(InformerManager.class);
    when(mim.keys()).thenReturn(Stream.of(resourceId));
    when(mim.contains(ghostResourceId)).thenReturn(false);
    when(informerEventSource.manager()).thenReturn(mim);

    var result = informerEventSource.keys().toList();

    assertThat(result).containsExactlyInAnyOrder(resourceId, ghostResourceId);
  }

  @Test
  void keysDoNotDuplicateExistingKeys() {
    var resource = testDeployment();
    var newerResource = testDeployment();
    newerResource.getMetadata().setResourceVersion("5");

    var resourceId = ResourceID.fromResource(resource);

    when(temporaryResourceCache.getResources()).thenReturn(Map.of(resourceId, newerResource));
    when(temporaryResourceCache.isEmpty()).thenReturn(false);

    var mim = mock(InformerManager.class);
    when(mim.keys()).thenReturn(Stream.of(resourceId));
    when(mim.contains(resourceId)).thenReturn(true);
    when(informerEventSource.manager()).thenReturn(mim);

    var result = informerEventSource.keys().toList();

    assertThat(result).containsExactly(resourceId);
  }

  @Test
  void checkGhostResourcesPropagatesDeleteForMissingTempCacheEntry() {
    // A resource lingers in the temp cache after our write but the informer never
    // observed it (e.g. the resource was deleted before the watch caught up).
    // checkGhostResources should remove it and surface a synthetic DELETE event
    // so the reconciler is notified.
    var ghost = testDeployment();
    ghost.getMetadata().setNamespace("default");
    ghost.getMetadata().setResourceVersion("3");

    var tempCache = new TemporaryResourceCache<>(true, informerEventSource);
    informerEventSource.setTemporalResourceCache(tempCache);

    var manager = mock(InformerManager.class);
    when(manager.isWatchingNamespace(any())).thenReturn(true);
    when(manager.lastSyncResourceVersion(any())).thenReturn("1");
    when(manager.get(any())).thenReturn(Optional.empty());
    when(informerEventSource.manager()).thenReturn(manager);

    tempCache.putResource(ghost);
    assertThat(tempCache.getResources()).containsKey(ResourceID.fromResource(ghost));

    // Informer's last-sync moves past the temp cache entry's RV and the resource
    // is missing from the informer's cache → it qualifies as a ghost.
    when(manager.lastSyncResourceVersion(any())).thenReturn("5");

    tempCache.checkGhostResources();

    assertThat(tempCache.getResources()).isEmpty();
    verify(eventHandlerMock, times(1)).handleEvent(any());
  }

  @Test
  void checkGhostResourcesKeepsResourcePresentInInformerCache() {
    // Same setup as the ghost test, but the informer's cache still has the
    // resource — it is NOT a ghost; the temp cache entry should be left alone
    // and no DELETE should propagate.
    var resource = testDeployment();
    resource.getMetadata().setNamespace("default");
    resource.getMetadata().setResourceVersion("3");

    var tempCache = new TemporaryResourceCache<>(true, informerEventSource);
    informerEventSource.setTemporalResourceCache(tempCache);

    var manager = mock(InformerManager.class);
    when(manager.isWatchingNamespace(any())).thenReturn(true);
    when(manager.lastSyncResourceVersion(any())).thenReturn("1");
    when(manager.get(any())).thenReturn(Optional.of(resource));
    when(informerEventSource.manager()).thenReturn(manager);

    tempCache.putResource(resource);
    when(manager.lastSyncResourceVersion(any())).thenReturn("5");

    tempCache.checkGhostResources();

    assertThat(tempCache.getResources()).containsKey(ResourceID.fromResource(resource));
    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void ghostCleanupDiscardsOrphanFilterWindow() {
    // We did an own write, recorded its rv into the filter window, but the informer never
    // delivered a watch event for it (resource deleted before the watch caught up).
    // Ghost cleanup must drop both the temp cache entry AND the orphan filter window;
    // otherwise the window leaks ownResourceVersions forever and a future event for a
    // recreated resource at the same id would be wrongly filtered.
    var ghost = testDeployment();
    ghost.getMetadata().setNamespace("default");
    ghost.getMetadata().setResourceVersion("3");
    var resourceId = ResourceID.fromResource(ghost);

    var tempCache = new TemporaryResourceCache<>(true, informerEventSource);
    informerEventSource.setTemporalResourceCache(tempCache);

    var manager = mock(InformerManager.class);
    when(manager.isWatchingNamespace(any())).thenReturn(true);
    when(manager.lastSyncResourceVersion(any())).thenReturn("1");
    when(manager.get(any())).thenReturn(Optional.empty());
    when(informerEventSource.manager()).thenReturn(manager);

    // Open a filter window for an in-flight write, then close it (our update returned but
    // the watch event never arrived).
    tempCache.startEventFilteringModify(resourceId);
    tempCache.putResource(ghost);
    tempCache.doneEventFilterModify(resourceId);
    assertThat(tempCache.getEventFilterSupport().isActiveUpdateFor(resourceId))
        .as("filter window must persist while ownResourceVersions is non-empty")
        .isTrue();

    when(manager.lastSyncResourceVersion(any())).thenReturn("5");

    tempCache.checkGhostResources();

    assertThat(tempCache.getResources()).isEmpty();
    assertThat(tempCache.getEventFilterSupport().isActiveUpdateFor(resourceId))
        .as("orphan filter window must be discarded by ghost cleanup")
        .isFalse();
    verify(eventHandlerMock, times(1)).handleEvent(any());
  }

  @Test
  void ghostCleanupSyntheticDeleteRespectsOnDeleteFilter() throws Exception {
    // The synthetic DELETE produced by ghost cleanup flows through handleEvent and must
    // honor the user's onDeleteFilter — same semantics as a real watch DELETE. The temp
    // cache is still drained and the index still drops the entry; only propagation is
    // skipped.
    var indexMock = injectIndexMock();
    var ghost = testDeployment();
    ghost.getMetadata().setNamespace("default");
    ghost.getMetadata().setResourceVersion("3");

    var tempCache = new TemporaryResourceCache<>(true, informerEventSource);
    informerEventSource.setTemporalResourceCache(tempCache);
    informerEventSource.setOnDeleteFilter((r, b) -> false);

    var manager = mock(InformerManager.class);
    when(manager.isWatchingNamespace(any())).thenReturn(true);
    when(manager.lastSyncResourceVersion(any())).thenReturn("1");
    when(manager.get(any())).thenReturn(Optional.empty());
    when(informerEventSource.manager()).thenReturn(manager);

    tempCache.putResource(ghost);
    when(manager.lastSyncResourceVersion(any())).thenReturn("5");

    tempCache.checkGhostResources();

    assertThat(tempCache.getResources()).isEmpty();
    verify(indexMock, times(1)).onDelete(ghost);
    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void ghostCleanupRetainsActiveFilterWindowWhenResourcePresentInInformer() {
    // Mirror of checkGhostResourcesKeepsResourcePresentInInformerCache, but with an active
    // filter window: if the resource is still in the informer cache, the temp entry stays
    // AND the filter window must stay too — the in-flight write echo is still expected.
    var resource = testDeployment();
    resource.getMetadata().setNamespace("default");
    resource.getMetadata().setResourceVersion("3");
    var resourceId = ResourceID.fromResource(resource);

    var tempCache = new TemporaryResourceCache<>(true, informerEventSource);
    informerEventSource.setTemporalResourceCache(tempCache);

    var manager = mock(InformerManager.class);
    when(manager.isWatchingNamespace(any())).thenReturn(true);
    when(manager.lastSyncResourceVersion(any())).thenReturn("1");
    when(manager.get(any())).thenReturn(Optional.of(resource));
    when(informerEventSource.manager()).thenReturn(manager);

    tempCache.startEventFilteringModify(resourceId);
    tempCache.putResource(resource);
    tempCache.doneEventFilterModify(resourceId);

    when(manager.lastSyncResourceVersion(any())).thenReturn("5");
    tempCache.checkGhostResources();

    assertThat(tempCache.getResources()).containsKey(resourceId);
    assertThat(tempCache.getEventFilterSupport().isActiveUpdateFor(resourceId))
        .as("non-ghost: filter window must be preserved")
        .isTrue();
    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void foreignUpdateDuringOwnUpdateIsPropagated() {
    // Sanity check that an external update arriving while our write is in flight
    // is surfaced to the reconciler — it isn't an own echo, so the filter must
    // let it through.
    withRealTemporaryResourceCache();

    CountDownLatch latch = sendForEventFilteringUpdate(2);
    informerEventSource.onUpdate(
        deploymentWithResourceVersion(2), deploymentWithResourceVersion(3));
    latch.countDown();

    expectHandleUpdateEvent(3, 2);
  }

  @Test
  void deleteEventDuringOwnUpdateIsPropagated() {
    // A DELETE arriving while our write is in flight must surface — the
    // resource has gone, so the filter should not silence it just because our
    // own write is still tracking RVs.
    withRealTemporaryResourceCache();

    CountDownLatch latch = sendForEventFilteringUpdate(2);
    informerEventSource.onDelete(deploymentWithResourceVersion(3), false);
    latch.countDown();

    await()
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(() -> verify(eventHandlerMock, atLeastOnce()).handleEvent(any()));
  }

  @Test
  void handleEventUpdatesIndexWhenDeletePropagatesFromTempCache() throws Exception {
    // handleEvent is invoked from ManagedInformerEventSource#eventFilteringUpdateAndCacheResource
    // only after the temp cache decided to surface the event. For a DELETE that means the resource
    // is really gone and the secondary→primary index must drop it; otherwise stale entries linger
    // and getSecondaryResources keeps returning a tombstone.
    var indexMock = injectIndexMock();
    var resource = testDeployment();

    informerEventSource.handleEvent(ResourceAction.DELETED, resource, null, false);

    verify(indexMock, times(1)).onDelete(resource);
    verify(indexMock, never()).onAddOrUpdate(any(), any());
    verify(eventHandlerMock, times(1)).handleEvent(any());
  }

  @Test
  void handleEventDoesNotTouchIndexForNonDeleteAction() throws Exception {
    // The onAdd/onUpdate path maintains the index in onAddOrUpdate(); handleEvent must not
    // double-update it for non-DELETE actions, otherwise we'd index resources twice.
    var indexMock = injectIndexMock();

    informerEventSource.handleEvent(
        ResourceAction.UPDATED, testDeployment(), testDeployment(), null);

    verify(indexMock, never()).onDelete(any());
    verify(indexMock, never()).onAddOrUpdate(any(), any());
    verify(eventHandlerMock, times(1)).handleEvent(any());
  }

  @Test
  void handleEventRespectsOnDeleteFilter() throws Exception {
    // The temp-cache pipeline must honor user-level filters: if onDeleteFilter rejects, the
    // synthesized DELETE must not be surfaced. The index, however, is still updated because the
    // resource is really gone — same semantics as the direct onDelete watch path.
    var indexMock = injectIndexMock();
    informerEventSource.setOnDeleteFilter((r, b) -> false);
    var resource = testDeployment();

    informerEventSource.handleEvent(ResourceAction.DELETED, resource, null, false);

    verify(indexMock, times(1)).onDelete(resource);
    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void handleEventRespectsOnUpdateFilter() throws Exception {
    var indexMock = injectIndexMock();
    informerEventSource.setOnUpdateFilter((n, o) -> false);

    informerEventSource.handleEvent(
        ResourceAction.UPDATED, testDeployment(), testDeployment(), null);

    verify(indexMock, never()).onDelete(any());
    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void handleEventRespectsOnAddFilter() throws Exception {
    var indexMock = injectIndexMock();
    informerEventSource.setOnAddFilter(r -> false);

    informerEventSource.handleEvent(ResourceAction.ADDED, testDeployment(), null, null);

    verify(indexMock, never()).onDelete(any());
    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void handleEventRespectsGenericFilter() throws Exception {
    // The generic filter applies regardless of action and short-circuits per-action filters.
    // For DELETE the index is still updated (resource really gone), but no event is propagated
    // for any action.
    var indexMock = injectIndexMock();
    informerEventSource.setGenericFilter(r -> false);
    var resource = testDeployment();

    informerEventSource.handleEvent(ResourceAction.DELETED, resource, null, true);
    informerEventSource.handleEvent(ResourceAction.UPDATED, resource, resource, null);
    informerEventSource.handleEvent(ResourceAction.ADDED, resource, null, null);

    verify(indexMock, times(1)).onDelete(resource);
    verify(eventHandlerMock, never()).handleEvent(any());
  }

  private PrimaryToSecondaryIndex<Deployment> injectIndexMock() throws Exception {
    @SuppressWarnings("unchecked")
    PrimaryToSecondaryIndex<Deployment> indexMock = mock(PrimaryToSecondaryIndex.class);
    Field field = InformerEventSource.class.getDeclaredField("primaryToSecondaryIndex");
    field.setAccessible(true);
    field.set(informerEventSource, indexMock);
    return indexMock;
  }

  private void assertNoEventProduced() {
    await()
        .pollDelay(Duration.ofMillis(70))
        .timeout(Duration.ofMillis(150))
        .untilAsserted(() -> verify(informerEventSource, never()).propagateEvent(any(), any()));
  }

  private void expectPropagateEvent(Deployment newResourceVersion) {
    await()
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(
            () ->
                verify(informerEventSource, times(1))
                    .propagateEvent(eq(newResourceVersion), any()));
  }

  private void expectHandleUpdateEvent(int newResourceVersion, int oldResourceVersion) {
    await()
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(
            () ->
                verify(informerEventSource, times(1))
                    .handleEvent(
                        eq(ResourceAction.UPDATED),
                        argThat(
                            r ->
                                ("" + newResourceVersion)
                                    .equals(r.getMetadata().getResourceVersion())),
                        argThat(
                            r ->
                                ("" + oldResourceVersion)
                                    .equals(r.getMetadata().getResourceVersion())),
                        any()));
  }

  private CountDownLatch sendForEventFilteringUpdate(int resourceVersion) {
    return EventFilterTestUtils.sendForEventFilteringUpdate(
        informerEventSource,
        testDeployment(),
        r -> withResourceVersion(testDeployment(), resourceVersion));
  }

  private CountDownLatch sendForExceptionThrowingUpdate() {
    return EventFilterTestUtils.sendForEventFilteringUpdate(
        informerEventSource,
        testDeployment(),
        r -> {
          throw new KubernetesClientException("fake");
        });
  }

  private void withRealTemporaryResourceCache() {
    var mes = mock(ManagedInformerEventSource.class);
    var mim = mock(InformerManager.class);
    when(mes.manager()).thenReturn(mim);
    when(mim.isWatchingNamespace(any())).thenReturn(true);
    when(mim.lastSyncResourceVersion(any())).thenReturn("1");

    temporaryResourceCache = spy(new TemporaryResourceCache<>(true, mes));
    informerEventSource.setTemporalResourceCache(temporaryResourceCache);
  }

  private Deployment deploymentWithResourceVersion(int resourceVersion) {
    return withResourceVersion(testDeployment(), resourceVersion);
  }

  private Deployment testDeployment() {
    Deployment deployment = new Deployment();
    deployment.setMetadata(new ObjectMeta());
    deployment.getMetadata().setResourceVersion(DEFAULT_RESOURCE_VERSION);
    deployment.getMetadata().setName("test");
    return deployment;
  }
}

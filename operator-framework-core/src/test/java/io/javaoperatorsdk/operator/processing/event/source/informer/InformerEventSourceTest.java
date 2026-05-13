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
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
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
import io.javaoperatorsdk.operator.processing.event.source.EventFilterTestUtils;
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.TemporaryResourceCache.EventHandling;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;
import static io.javaoperatorsdk.operator.processing.event.source.EventFilterTestUtils.withResourceVersion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class InformerEventSourceTest {

  private static final String PREV_RESOURCE_VERSION = "0";
  private static final String DEFAULT_RESOURCE_VERSION = "1";

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
    when(secondaryToPrimaryMapper.toPrimaryResourceIDs(any()))
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
  void skipsEventPropagation() {
    when(temporaryResourceCache.getResourceFromCache(any()))
        .thenReturn(Optional.of(testDeployment()));

    when(temporaryResourceCache.onAddOrUpdateEvent(any(), any(), any()))
        .thenReturn(EventHandling.OBSOLETE);

    informerEventSource.onAdd(testDeployment());
    informerEventSource.onUpdate(testDeployment(), testDeployment());

    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void processEventPropagationWithoutAnnotation() {
    when(temporaryResourceCache.onAddOrUpdateEvent(any(), any(), any()))
        .thenReturn(EventHandling.NEW);
    informerEventSource.onUpdate(testDeployment(), testDeployment());

    verify(eventHandlerMock, times(1)).handleEvent(any());
  }

  @Test
  void processEventPropagationWithIncorrectAnnotation() {
    when(temporaryResourceCache.onAddOrUpdateEvent(any(), any(), any()))
        .thenReturn(EventHandling.NEW);
    informerEventSource.onAdd(
        new DeploymentBuilder(testDeployment())
            .editMetadata()
            .addToAnnotations(InformerEventSource.PREVIOUS_ANNOTATION_KEY, "invalid")
            .endMetadata()
            .build());

    verify(eventHandlerMock, times(1)).handleEvent(any());
  }

  @Test
  void propagateEventAndRemoveResourceFromTempCacheIfResourceVersionMismatch() {
    withRealTemporaryResourceCache();

    Deployment cachedDeployment = testDeployment();
    cachedDeployment.getMetadata().setResourceVersion(PREV_RESOURCE_VERSION);
    temporaryResourceCache.putResource(cachedDeployment);

    informerEventSource.onUpdate(cachedDeployment, testDeployment());

    verify(eventHandlerMock, times(1)).handleEvent(any());
    verify(temporaryResourceCache, times(1)).onAddOrUpdateEvent(any(), eq(testDeployment()), any());
  }

  @Test
  void genericFilterForEvents() {
    informerEventSource.setGenericFilter(r -> false);
    when(temporaryResourceCache.getResourceFromCache(any())).thenReturn(Optional.empty());

    informerEventSource.onAdd(testDeployment());
    informerEventSource.onUpdate(testDeployment(), testDeployment());
    informerEventSource.onDelete(testDeployment(), true);

    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void filtersOnAddEvents() {
    informerEventSource.setOnAddFilter(r -> false);
    when(temporaryResourceCache.getResourceFromCache(any())).thenReturn(Optional.empty());

    informerEventSource.onAdd(testDeployment());

    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void filtersOnUpdateEvents() {
    informerEventSource.setOnUpdateFilter((r1, r2) -> false);
    when(temporaryResourceCache.getResourceFromCache(any())).thenReturn(Optional.empty());

    informerEventSource.onUpdate(testDeployment(), testDeployment());

    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void filtersOnDeleteEvents() {
    informerEventSource.setOnDeleteFilter((r, b) -> false);
    when(temporaryResourceCache.getResourceFromCache(any())).thenReturn(Optional.empty());

    informerEventSource.onDelete(testDeployment(), true);

    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void handlesPrevResourceVersionForUpdate() {
    withRealTemporaryResourceCache();

    CountDownLatch latch = sendForEventFilteringUpdate(2);
    informerEventSource.onUpdate(
        deploymentWithResourceVersion(2), deploymentWithResourceVersion(3));
    latch.countDown();

    expectHandleEvent(3, 2);
  }

  @Test
  void handlesPrevResourceVersionForUpdateInCaseOfException() {
    withRealTemporaryResourceCache();

    CountDownLatch latch =
        EventFilterTestUtils.sendForEventFilteringUpdate(
            informerEventSource,
            testDeployment(),
            r -> {
              throw new KubernetesClientException("fake");
            });
    informerEventSource.onUpdate(
        deploymentWithResourceVersion(1), deploymentWithResourceVersion(2));
    latch.countDown();

    expectHandleEvent(2, 1);
  }

  @Test
  void handlesPrevResourceVersionForUpdateInCaseOfMultipleUpdates() {
    withRealTemporaryResourceCache();

    var deployment = testDeployment();
    CountDownLatch latch = sendForEventFilteringUpdate(deployment, 2);
    informerEventSource.onUpdate(
        withResourceVersion(testDeployment(), 2), withResourceVersion(testDeployment(), 3));
    informerEventSource.onUpdate(
        withResourceVersion(testDeployment(), 3), withResourceVersion(testDeployment(), 4));
    latch.countDown();

    expectHandleEvent(4, 2);
  }

  @Test
  void doesNotPropagateEventIfReceivedBeforeUpdate() {
    withRealTemporaryResourceCache();

    CountDownLatch latch = sendForEventFilteringUpdate(2);
    informerEventSource.onUpdate(
        deploymentWithResourceVersion(1), deploymentWithResourceVersion(2));
    latch.countDown();

    assertNoEventProduced();
  }

  @Test
  void filterAddEventBeforeUpdate() {
    withRealTemporaryResourceCache();

    CountDownLatch latch = sendForEventFilteringUpdate(2);
    informerEventSource.onAdd(deploymentWithResourceVersion(1));
    latch.countDown();

    assertNoEventProduced();
  }

  @Test
  void multipleCachingFilteringUpdates() {
    withRealTemporaryResourceCache();
    CountDownLatch latch = sendForEventFilteringUpdate(2);
    CountDownLatch latch2 =
        sendForEventFilteringUpdate(withResourceVersion(testDeployment(), 2), 3);

    informerEventSource.onUpdate(
        deploymentWithResourceVersion(1), deploymentWithResourceVersion(2));
    latch.countDown();
    latch2.countDown();
    informerEventSource.onUpdate(
        deploymentWithResourceVersion(2), deploymentWithResourceVersion(3));

    assertNoEventProduced();
  }

  @Test
  void multipleCachingFilteringUpdates_variant2() {
    withRealTemporaryResourceCache();

    CountDownLatch latch = sendForEventFilteringUpdate(2);
    CountDownLatch latch2 =
        sendForEventFilteringUpdate(withResourceVersion(testDeployment(), 2), 3);

    informerEventSource.onUpdate(
        deploymentWithResourceVersion(1), deploymentWithResourceVersion(2));
    latch.countDown();
    informerEventSource.onUpdate(
        deploymentWithResourceVersion(2), deploymentWithResourceVersion(3));
    latch2.countDown();

    assertNoEventProduced();
  }

  @Test
  void multipleCachingFilteringUpdates_variant3() {
    withRealTemporaryResourceCache();

    CountDownLatch latch = sendForEventFilteringUpdate(2);
    CountDownLatch latch2 =
        sendForEventFilteringUpdate(withResourceVersion(testDeployment(), 2), 3);

    latch.countDown();
    informerEventSource.onUpdate(
        deploymentWithResourceVersion(1), deploymentWithResourceVersion(2));
    informerEventSource.onUpdate(
        deploymentWithResourceVersion(2), deploymentWithResourceVersion(3));
    latch2.countDown();

    assertNoEventProduced();
  }

  @Test
  void multipleCachingFilteringUpdates_variant4() {
    withRealTemporaryResourceCache();

    CountDownLatch latch = sendForEventFilteringUpdate(2);
    CountDownLatch latch2 =
        sendForEventFilteringUpdate(withResourceVersion(testDeployment(), 2), 3);

    informerEventSource.onUpdate(
        deploymentWithResourceVersion(1), deploymentWithResourceVersion(2));
    informerEventSource.onUpdate(
        deploymentWithResourceVersion(2), deploymentWithResourceVersion(3));
    latch.countDown();
    latch2.countDown();

    assertNoEventProduced();
  }

  @Test
  void ghostCheckRemovesCachedResourceDuringFilteringUpdate() {
    var mes = mock(ManagedInformerEventSource.class);
    var mim = mock(InformerManager.class);
    when(mes.manager()).thenReturn(mim);
    when(mim.isWatchingNamespace(any())).thenReturn(true);
    when(mim.lastSyncResourceVersion(any())).thenReturn("1");
    when(mim.get(any())).thenReturn(Optional.empty());

    temporaryResourceCache = spy(new TemporaryResourceCache<>(true, mes));
    informerEventSource.setTemporalResourceCache(temporaryResourceCache);

    // put resource in cache and start a filtering update
    var deployment = deploymentWithResourceVersion(2);
    temporaryResourceCache.putResource(deployment);
    var resourceId = ResourceID.fromResource(deployment);
    temporaryResourceCache.startEventFilteringModify(resourceId);

    // advance sync version so ghost check considers the cached resource outdated
    when(mim.lastSyncResourceVersion(any())).thenReturn("3");

    // ghost check should remove the cached resource
    temporaryResourceCache.checkGhostResources();
    assertThat(temporaryResourceCache.getResourceFromCache(resourceId)).isEmpty();

    // complete the filtering update - the resource should not reappear
    temporaryResourceCache.doneEventFilterModify(resourceId, "2");
    assertThat(temporaryResourceCache.getResourceFromCache(resourceId)).isEmpty();
  }

  @Test
  void ghostCheckRunsConcurrentlyWithPutResource() {
    var mes = mock(ManagedInformerEventSource.class);
    var mim = mock(InformerManager.class);
    when(mes.manager()).thenReturn(mim);
    when(mim.isWatchingNamespace(any())).thenReturn(true);
    when(mim.lastSyncResourceVersion(any())).thenReturn("1");
    when(mim.get(any())).thenReturn(Optional.empty());

    temporaryResourceCache = spy(new TemporaryResourceCache<>(true, mes));
    informerEventSource.setTemporalResourceCache(temporaryResourceCache);

    // put a resource that will become a ghost
    var deployment = deploymentWithResourceVersion(2);
    temporaryResourceCache.putResource(deployment);

    // advance sync version so ghost check removes it
    when(mim.lastSyncResourceVersion(any())).thenReturn("3");

    temporaryResourceCache.checkGhostResources();
    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(deployment)))
        .isEmpty();

    // now put a newer resource - should succeed even after ghost removal
    var newerDeployment = deploymentWithResourceVersion(4);
    temporaryResourceCache.putResource(newerDeployment);
    assertThat(
            temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(newerDeployment)))
        .isPresent();
  }

  @Test
  void filteringUpdateAndGhostCheckWithNamespaceChange() {
    var mes = mock(ManagedInformerEventSource.class);
    var mim = mock(InformerManager.class);
    when(mes.manager()).thenReturn(mim);
    when(mim.isWatchingNamespace(any())).thenReturn(true);
    when(mim.lastSyncResourceVersion(any())).thenReturn("1");
    when(mim.get(any())).thenReturn(Optional.empty());

    temporaryResourceCache = spy(new TemporaryResourceCache<>(true, mes));
    informerEventSource.setTemporalResourceCache(temporaryResourceCache);

    // start filtering update and put resource
    var deployment = deploymentWithResourceVersion(2);
    var resourceId = ResourceID.fromResource(deployment);
    temporaryResourceCache.startEventFilteringModify(resourceId);
    temporaryResourceCache.putResource(deployment);

    // namespace becomes unwatched - ghost check should clean up
    when(mim.isWatchingNamespace(any())).thenReturn(false);

    temporaryResourceCache.checkGhostResources();
    assertThat(temporaryResourceCache.getResourceFromCache(resourceId)).isEmpty();

    // complete the filtering update
    var doneResult = temporaryResourceCache.doneEventFilterModify(resourceId, "2");
    // resource was already cleaned by ghost check, so no deferred event
    assertThat(doneResult).isEmpty();

    // put should be rejected since namespace is no longer watched
    temporaryResourceCache.putResource(deploymentWithResourceVersion(3));
    assertThat(temporaryResourceCache.getResourceFromCache(resourceId)).isEmpty();
  }

  private void assertNoEventProduced() {
    await()
        .pollDelay(Duration.ofMillis(50))
        .timeout(Duration.ofMillis(51))
        .untilAsserted(
            () -> verify(informerEventSource, never()).handleEvent(any(), any(), any(), any()));
  }

  private void expectHandleEvent(int newResourceVersion, int oldResourceVersion) {
    await()
        .untilAsserted(
            () -> {
              verify(informerEventSource, times(1))
                  .handleEvent(
                      eq(ResourceAction.UPDATED),
                      argThat(
                          newResource -> {
                            assertThat(newResource.getMetadata().getResourceVersion())
                                .isEqualTo("" + newResourceVersion);
                            return true;
                          }),
                      argThat(
                          newResource -> {
                            assertThat(newResource.getMetadata().getResourceVersion())
                                .isEqualTo("" + oldResourceVersion);
                            return true;
                          }),
                      isNull());
            });
  }

  private CountDownLatch sendForEventFilteringUpdate(int resourceVersion) {
    return sendForEventFilteringUpdate(testDeployment(), resourceVersion);
  }

  private CountDownLatch sendForEventFilteringUpdate(Deployment deployment, int resourceVersion) {
    return EventFilterTestUtils.sendForEventFilteringUpdate(
        informerEventSource, deployment, r -> withResourceVersion(deployment, resourceVersion));
  }

  private void withRealTemporaryResourceCache() {
    var mes = mock(ManagedInformerEventSource.class);
    var mim = mock(InformerManager.class);
    when(mes.manager()).thenReturn(mim);
    when(mim.lastSyncResourceVersion(any())).thenReturn("1");

    temporaryResourceCache = spy(new TemporaryResourceCache<>(true, mes));
    informerEventSource.setTemporalResourceCache(temporaryResourceCache);
  }

  Deployment deploymentWithResourceVersion(int resourceVersion) {
    return withResourceVersion(testDeployment(), resourceVersion);
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
    when(mim.list(nullable(String.class), any())).thenReturn(Stream.of(original));
    doReturn(mim).when(informerEventSource).manager();

    var result = informerEventSource.list(null, r -> true).toList();

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
    doReturn(mim).when(informerEventSource).manager();

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
    doReturn(mim).when(informerEventSource).manager();

    var result = informerEventSource.list(null, r -> true).toList();

    assertThat(result).containsExactly(original);
  }

  @Test
  void listReplacesOnlyMatchingResources() {
    var dep1 = testDeployment();
    var dep2 = testDeployment();
    dep2.getMetadata().setName("other");
    var newerDep1 = testDeployment();
    newerDep1.getMetadata().setResourceVersion("5");

    when(temporaryResourceCache.getResources())
        .thenReturn(new HashMap<>(Map.of(ResourceID.fromResource(dep1), newerDep1)));

    var informerManager = mock(InformerManager.class);
    when(informerManager.list(nullable(String.class))).thenReturn(Stream.of(dep1, dep2));
    doReturn(informerManager).when(informerEventSource).manager();

    var result = informerEventSource.list(null, r -> true).toList();

    assertThat(result).containsExactlyInAnyOrder(newerDep1, dep2);
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
    doReturn(informerManager).when(informerEventSource).manager();
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
    doReturn(informerManager).when(informerEventSource).manager();
    informerEventSource.addIndexers(
        Map.of("idx", d -> List.of(d.getMetadata().getLabels().get("app"))));

    var result = informerEventSource.byIndexStream("idx", "key").toList();

    assertThat(result).isEmpty();
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
    doReturn(mim).when(informerEventSource).manager();

    var result = informerEventSource.list(null, r -> true).toList();

    assertThat(result).containsExactly(original);
  }

  @Test
  void byIndexStreamKeepsResourceWhenTempCacheHasOlderVersion() {
    var original = testDeployment();
    original.getMetadata().setResourceVersion("5");
    var olderTemp = testDeployment();
    olderTemp.getMetadata().setResourceVersion("3");

    when(temporaryResourceCache.getResources())
        .thenReturn(new HashMap<>(Map.of(ResourceID.fromResource(original), olderTemp)));

    var mim = mock(InformerManager.class);
    when(mim.byIndexStream(any(), any())).thenReturn(Stream.of(original));
    doReturn(mim).when(informerEventSource).manager();
    informerEventSource.addIndexers(Map.of("idx", d -> List.of("key")));

    var result = informerEventSource.byIndexStream("idx", "key").toList();

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
    doReturn(mim).when(informerEventSource).manager();

    var result = informerEventSource.list(null, r -> true).toList();

    assertThat(result).containsExactlyInAnyOrder(resource, ghostResource);
  }

  @Test
  void keysIncludesGhostResourceKeys() {
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
    doReturn(mim).when(informerEventSource).manager();

    var result = informerEventSource.keys().toList();

    assertThat(result).containsExactlyInAnyOrder(resourceId, ghostResourceId);
  }

  @Test
  void keysDoesNotDuplicateExistingKeys() {
    var resource = testDeployment();
    var newerResource = testDeployment();
    newerResource.getMetadata().setResourceVersion("5");

    var resourceId = ResourceID.fromResource(resource);

    when(temporaryResourceCache.getResources()).thenReturn(Map.of(resourceId, newerResource));
    when(temporaryResourceCache.isEmpty()).thenReturn(false);

    var mim = mock(InformerManager.class);
    when(mim.keys()).thenReturn(Stream.of(resourceId));
    when(mim.contains(resourceId)).thenReturn(true);
    doReturn(mim).when(informerEventSource).manager();

    var result = informerEventSource.keys().toList();

    assertThat(result).containsExactly(resourceId);
  }

  Deployment testDeployment() {
    Deployment deployment = new Deployment();
    deployment.setMetadata(new ObjectMeta());
    deployment.getMetadata().setResourceVersion(DEFAULT_RESOURCE_VERSION);
    deployment.getMetadata().setName("test");
    return deployment;
  }
}

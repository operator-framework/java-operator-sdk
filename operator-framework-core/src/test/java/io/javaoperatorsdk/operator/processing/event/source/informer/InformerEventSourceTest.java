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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.fabric8.kubernetes.api.model.HasMetadata;
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
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.TemporaryResourceCache.EventHandling;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
@TestInstance(value = TestInstance.Lifecycle.PER_METHOD)
class InformerEventSourceTest {

  private static final String PREV_RESOURCE_VERSION = "0";
  private static final String DEFAULT_RESOURCE_VERSION = "1";

  ExecutorService executorService = Executors.newCachedThreadPool();

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
    when(informerEventSourceConfiguration.getInformerConfig()).thenReturn(informerConfig);
    when(informerConfig.getEffectiveNamespaces(any())).thenReturn(DEFAULT_NAMESPACES_SET);
    when(informerEventSourceConfiguration.getSecondaryToPrimaryMapper())
        .thenReturn(mock(SecondaryToPrimaryMapper.class));
    when(informerEventSourceConfiguration.getResourceClass()).thenReturn(Deployment.class);

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
    SecondaryToPrimaryMapper secondaryToPrimaryMapper = mock(SecondaryToPrimaryMapper.class);
    when(informerEventSourceConfiguration.getSecondaryToPrimaryMapper())
        .thenReturn(secondaryToPrimaryMapper);
    when(secondaryToPrimaryMapper.toPrimaryResourceIDs(any()))
        .thenReturn(Set.of(ResourceID.fromResource(testDeployment())));
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
    Deployment cachedDeployment = testDeployment();
    cachedDeployment.getMetadata().setResourceVersion(PREV_RESOURCE_VERSION);
    when(temporaryResourceCache.getResourceFromCache(any()))
        .thenReturn(Optional.of(cachedDeployment));
    when(temporaryResourceCache.onAddOrUpdateEvent(any(), any(), any()))
        .thenReturn(EventHandling.NEW);

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
  void handlesPrevResourceVersionForUpdate() throws InterruptedException {
    withRealTemporaryResourceCache();
    var deployment = testDeployment();
    CountDownLatch latch = new CountDownLatch(1);

    executorService.submit(
        () ->
            informerEventSource.eventFilteringUpdateAndCacheResource(
                deployment,
                r -> {
                  var resp = testDeployment();
                  incResourceVersion(resp, 1);
                  try {
                    latch.await();
                  } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                  }
                  return resp;
                }));
    Thread.sleep(50);
    informerEventSource.onUpdate(
        incResourceVersion(deployment, 1), incResourceVersion(testDeployment(), 2));

    latch.countDown();

    await()
        .untilAsserted(
            () -> {
              verify(informerEventSource, times(1))
                  .handleEvent(
                      eq(ResourceAction.UPDATED),
                      argThat(
                          newResource -> {
                            assertThat(newResource.getMetadata().getResourceVersion())
                                .isEqualTo("3");
                            return true;
                          }),
                      argThat(
                          newResource -> {
                            assertThat(newResource.getMetadata().getResourceVersion())
                                .isEqualTo("2");
                            return true;
                          }),
                      isNull(),
                      eq(false));
            });
  }

  @Test
  void handlesPrevResourceVersionForUpdateInCaseOfException() throws InterruptedException {
    withRealTemporaryResourceCache();

    withRealTemporaryResourceCache();
    var deployment = testDeployment();
    CountDownLatch latch = new CountDownLatch(1);

    executorService.submit(
        () ->
            informerEventSource.eventFilteringUpdateAndCacheResource(
                deployment,
                r -> {
                  try {
                    latch.await();
                    throw new KubernetesClientException("fake");
                  } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                  }
                }));
    Thread.sleep(50);
    informerEventSource.onUpdate(deployment, incResourceVersion(testDeployment(), 1));
    latch.countDown();

    await()
        .untilAsserted(
            () -> {
              verify(informerEventSource, times(1))
                  .handleEvent(
                      eq(ResourceAction.UPDATED),
                      argThat(
                          newResource -> {
                            assertThat(newResource.getMetadata().getResourceVersion())
                                .isEqualTo("2");
                            return true;
                          }),
                      argThat(
                          newResource -> {
                            assertThat(newResource.getMetadata().getResourceVersion())
                                .isEqualTo("1");
                            return true;
                          }),
                      isNull(),
                      eq(false));
            });
  }

  @Test
  void handlesPrevResourceVersionForUpdateInCaseOfMultipleUpdates() throws InterruptedException {
    withRealTemporaryResourceCache();

    withRealTemporaryResourceCache();
    var deployment = testDeployment();
    CountDownLatch latch = new CountDownLatch(1);

    executorService.submit(
        () ->
            informerEventSource.eventFilteringUpdateAndCacheResource(
                deployment,
                r -> {
                  var resp = testDeployment();
                  incResourceVersion(resp, 1);
                  try {
                    latch.await();
                  } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                  }
                  return resp;
                }));
    Thread.sleep(50);
    informerEventSource.onUpdate(
        incResourceVersion(testDeployment(), 1), incResourceVersion(testDeployment(), 2));
    informerEventSource.onUpdate(
        incResourceVersion(testDeployment(), 2), incResourceVersion(testDeployment(), 3));
    latch.countDown();

    await()
        .untilAsserted(
            () -> {
              verify(informerEventSource, times(1))
                  .handleEvent(
                      eq(ResourceAction.UPDATED),
                      argThat(
                          newResource -> {
                            assertThat(newResource.getMetadata().getResourceVersion())
                                .isEqualTo("4");
                            return true;
                          }),
                      argThat(
                          newResource -> {
                            assertThat(newResource.getMetadata().getResourceVersion())
                                .isEqualTo("2");
                            return true;
                          }),
                      isNull(),
                      eq(false));
            });
  }

  @Test
  void doesNotPropagateEventIfReceivedBeforeUpdate() throws InterruptedException {
    withRealTemporaryResourceCache();
    var deployment = testDeployment();
    CountDownLatch latch = new CountDownLatch(1);

    executorService.submit(
        () ->
            informerEventSource.eventFilteringUpdateAndCacheResource(
                deployment,
                r -> {
                  var resp = testDeployment();
                  incResourceVersion(resp, 2);
                  try {
                    latch.await();
                  } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                  }
                  return resp;
                }));
    Thread.sleep(50);
    informerEventSource.onUpdate(deployment, incResourceVersion(testDeployment(), 1));
    latch.countDown();

    await()
        .pollDelay(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              verify(informerEventSource, never())
                  .handleEvent(any(), any(), any(), any(), anyBoolean());
            });
  }

  private void withRealTemporaryResourceCache() {
    temporaryResourceCache = new TemporaryResourceCache<>(true);
    informerEventSource.setTemporalResourceCache(temporaryResourceCache);
  }

  <R extends HasMetadata> R incResourceVersion(R resource, int increment) {
    var v = resource.getMetadata().getResourceVersion();
    if (v == null) {
      throw new IllegalArgumentException("Resource version is null");
    }
    resource.getMetadata().setResourceVersion(versionPlus(v, increment));
    return resource;
  }

  String versionPlus(String resourceVersion, int increment) {
    return "" + (Integer.parseInt(resourceVersion) + increment);
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

  Deployment testDeployment() {
    Deployment deployment = new Deployment();
    deployment.setMetadata(new ObjectMeta());
    deployment.getMetadata().setResourceVersion(DEFAULT_RESOURCE_VERSION);
    deployment.getMetadata().setName("test");
    return deployment;
  }
}

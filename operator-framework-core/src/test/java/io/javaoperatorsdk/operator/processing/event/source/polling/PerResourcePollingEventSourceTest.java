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
package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.ResourceIDMapper;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSourceTestBase;
import io.javaoperatorsdk.operator.processing.event.source.IndexerResourceCache;
import io.javaoperatorsdk.operator.processing.event.source.SampleExternalResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PerResourcePollingEventSourceTest
    extends AbstractEventSourceTestBase<
        PerResourcePollingEventSource<SampleExternalResource, TestCustomResource, String>,
        EventHandler> {

  public static final int PERIOD = 150;

  @SuppressWarnings("unchecked")
  private final PerResourcePollingEventSource.ResourceFetcher<
          SampleExternalResource, TestCustomResource>
      supplier = mock(PerResourcePollingEventSource.ResourceFetcher.class);

  @SuppressWarnings("unchecked")
  private final IndexerResourceCache<TestCustomResource> resourceCache =
      mock(IndexerResourceCache.class);

  private final TestCustomResource testCustomResource = TestUtils.testCustomResource();
  private final EventSourceContext<TestCustomResource> context = mock(EventSourceContext.class);

  @BeforeEach
  public void setup() {
    when(resourceCache.get(any())).thenReturn(Optional.of(testCustomResource));
    when(supplier.fetchResources(any())).thenReturn(Set.of(SampleExternalResource.testResource1()));
    when(context.getPrimaryCache()).thenReturn(resourceCache);

    setUpSource(
        new PerResourcePollingEventSource<>(
            SampleExternalResource.class,
            context,
            new PerResourcePollingConfigurationBuilder<
                    SampleExternalResource, TestCustomResource, String>(
                    supplier, Duration.ofMillis(PERIOD))
                .withResourceIDMapper(r -> r.getName() + "#" + r.getValue())
                .build()));
  }

  @Test
  void pollsTheResourceAfterAwareOfIt() {
    source.onResourceCreated(testCustomResource);

    await()
        .pollDelay(Duration.ofMillis(3 * PERIOD))
        .untilAsserted(
            () -> {
              verify(supplier, atLeast(2)).fetchResources(eq(testCustomResource));
              verify(supplier, atLeast(2)).fetchDelay(any(), eq(testCustomResource));
              verify(eventHandler, times(1)).handleEvent(any());
            });
  }

  @Test
  void resourcesSeenBeforeStartArePolledOnceStarted() {
    source.stop();
    setUpSource(
        new PerResourcePollingEventSource<>(
            SampleExternalResource.class,
            context,
            new PerResourcePollingConfigurationBuilder<
                    SampleExternalResource, TestCustomResource, String>(
                    supplier, Duration.ofMillis(PERIOD))
                .withResourceIDMapper(r -> r.getName() + "#" + r.getValue())
                .build()),
        false);

    source.onResourceCreated(testCustomResource);
    verify(supplier, never()).fetchResources(any());

    source.start();

    await()
        .untilAsserted(() -> verify(supplier, atLeast(1)).fetchResources(eq(testCustomResource)));
  }

  @Test
  void resourcesDeletedBeforeStartAreNotPolled() {
    source.stop();
    setUpSource(
        new PerResourcePollingEventSource<>(
            SampleExternalResource.class,
            context,
            new PerResourcePollingConfigurationBuilder<
                    SampleExternalResource, TestCustomResource, String>(
                    supplier, Duration.ofMillis(PERIOD))
                .withResourceIDMapper(r -> r.getName() + "#" + r.getValue())
                .build()),
        false);

    source.onResourceCreated(testCustomResource);
    source.onResourceDeleted(testCustomResource);
    source.start();

    await()
        .pollDelay(Duration.ofMillis(2 * PERIOD))
        .untilAsserted(() -> verify(supplier, never()).fetchResources(any()));
  }

  @Test
  void registeringTaskOnAPredicate() {
    setUpSource(
        new PerResourcePollingEventSource<>(
            SampleExternalResource.class,
            context,
            new PerResourcePollingConfigurationBuilder<
                    SampleExternalResource, TestCustomResource, String>(
                    supplier, Duration.ofMillis(PERIOD))
                .withRegisterPredicate(
                    testCustomResource -> testCustomResource.getMetadata().getGeneration() > 1)
                .withResourceIDMapper(ResourceIDMapper.resourceIdProviderMapper())
                .build()));

    source.onResourceCreated(testCustomResource);

    await()
        .pollDelay(Duration.ofMillis(2 * PERIOD))
        .untilAsserted(() -> verify(supplier, times(0)).fetchResources(eq(testCustomResource)));

    testCustomResource.getMetadata().setGeneration(2L);
    source.onResourceUpdated(testCustomResource, testCustomResource);

    await()
        .pollDelay(Duration.ofMillis(2 * PERIOD))
        .untilAsserted(() -> verify(supplier, atLeast(1)).fetchResources(eq(testCustomResource)));
  }

  @Test
  void propagateEventOnDeletedResource() {
    source.onResourceCreated(testCustomResource);
    when(supplier.fetchResources(any()))
        .thenReturn(Set.of(SampleExternalResource.testResource1()))
        .thenReturn(Collections.emptySet());

    await()
        .pollDelay(Duration.ofMillis(3 * PERIOD))
        .untilAsserted(
            () -> {
              verify(supplier, atLeast(2)).fetchResources(eq(testCustomResource));
              verify(eventHandler, times(2)).handleEvent(any());
            });
  }

  @Test
  void getSecondaryResourceInitiatesFetchJustForFirstTime() {
    source.onResourceCreated(testCustomResource);
    when(supplier.fetchResources(any()))
        .thenReturn(Set.of(SampleExternalResource.testResource1()))
        .thenReturn(
            Set.of(SampleExternalResource.testResource1(), SampleExternalResource.testResource2()));

    var value = source.getSecondaryResources(testCustomResource);

    verify(supplier, times(1)).fetchResources(eq(testCustomResource));
    verify(eventHandler, never()).handleEvent(any());
    assertThat(value).hasSize(1);

    value = source.getSecondaryResources(testCustomResource);

    assertThat(value).hasSize(1);
    verify(supplier, times(1)).fetchResources(eq(testCustomResource));
    verify(eventHandler, never()).handleEvent(any());

    await()
        .pollDelay(Duration.ofMillis(PERIOD * 2))
        .untilAsserted(
            () -> {
              verify(supplier, atLeast(2)).fetchResources(eq(testCustomResource));
              var val = source.getSecondaryResources(testCustomResource);
              assertThat(val).hasSize(2);
            });
  }

  @Test
  void getsValueFromCacheOrSupplier() {
    source.onResourceCreated(testCustomResource);
    when(supplier.fetchResources(any()))
        .thenReturn(Collections.emptySet())
        .thenReturn(Set.of(SampleExternalResource.testResource1()));

    await()
        .pollDelay(Duration.ofMillis(PERIOD / 3))
        .untilAsserted(
            () -> {
              var value = source.getSecondaryResources(testCustomResource);
              verify(eventHandler, times(0)).handleEvent(any());
              assertThat(value).isEmpty();
            });

    await()
        .pollDelay(Duration.ofMillis(PERIOD * 2))
        .untilAsserted(
            () -> {
              var value2 = source.getSecondaryResources(testCustomResource);
              assertThat(value2).hasSize(1);
              verify(eventHandler, times(1)).handleEvent(any());
            });
  }

  @Test
  void supportsDynamicPollingDelay() {
    when(supplier.fetchResources(any())).thenReturn(Set.of(SampleExternalResource.testResource1()));
    when(supplier.fetchDelay(any(), any()))
        .thenReturn(Optional.of(Duration.ofMillis(PERIOD)))
        .thenReturn(Optional.of(Duration.ofMillis(PERIOD * 2)));

    source.onResourceCreated(testCustomResource);

    await()
        .pollDelay(Duration.ofMillis(PERIOD))
        .atMost(Duration.ofMillis((long) (1.5 * PERIOD)))
        .pollInterval(Duration.ofMillis(20))
        .untilAsserted(() -> verify(supplier, times(1)).fetchResources(any()));
    // verifying that it is not called as with normal interval
    await()
        .pollDelay(Duration.ofMillis(PERIOD))
        .atMost(Duration.ofMillis((long) (1.5 * PERIOD)))
        .pollInterval(Duration.ofMillis(20))
        .untilAsserted(() -> verify(supplier, times(1)).fetchResources(any()));
    await()
        .pollDelay(Duration.ofMillis(PERIOD))
        .atMost(Duration.ofMillis(2 * PERIOD))
        .pollInterval(Duration.ofMillis(20))
        .untilAsserted(() -> verify(supplier, times(2)).fetchResources(any()));
  }

  @Test
  void pollsAgainAfterRestart() {
    source.onResourceCreated(testCustomResource);
    await().untilAsserted(() -> verify(supplier, atLeast(2)).fetchResources(any()));
    source.stop();

    source.start();
    clearInvocations(supplier);
    source.onResourceCreated(testCustomResource);

    await().untilAsserted(() -> verify(supplier, atLeast(2)).fetchResources(any()));
  }

  @Test
  void aFetchOutlivingARestartDoesNotStartASecondPollingChain() throws Exception {
    var fetching = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(supplier.fetchResources(any()))
        .thenAnswer(
            invocation -> {
              fetching.countDown();
              var interrupted = false;
              while (release.getCount() > 0) {
                try {
                  release.await();
                } catch (InterruptedException e) {
                  interrupted = true;
                }
              }
              if (interrupted) {
                Thread.currentThread().interrupt();
              }
              return Set.of(SampleExternalResource.testResource1());
            })
        .thenReturn(Set.of(SampleExternalResource.testResource1()));

    source.onResourceCreated(testCustomResource);
    assertThat(fetching.await(5, TimeUnit.SECONDS)).isTrue();
    source.stop();
    source.start();
    source.onResourceCreated(testCustomResource);
    clearInvocations(supplier);
    release.countDown();

    await()
        .pollDelay(Duration.ofMillis(10 * PERIOD))
        .untilAsserted(() -> verify(supplier, atMost(15)).fetchResources(any()));
  }

  @Test
  void aRegistrationRacingStopDoesNotBlockPollingAfterARestart() throws Exception {
    var configurationService = new BaseConfigurationService();
    var executorServiceManager = configurationService.getExecutorServiceManager();
    var controllerConfiguration = mock(ControllerConfiguration.class);
    when(controllerConfiguration.getConfigurationService()).thenReturn(configurationService);
    when(context.getControllerConfiguration()).thenReturn(controllerConfiguration);
    setUpSource(
        new PerResourcePollingEventSource<>(
            SampleExternalResource.class,
            context,
            new PerResourcePollingConfigurationBuilder<
                    SampleExternalResource, TestCustomResource, String>(
                    supplier, Duration.ofMillis(PERIOD))
                .withResourceIDMapper(r -> r.getName() + "#" + r.getValue())
                .build()));

    try {
      Thread informer;
      synchronized (source) {
        informer = new Thread(() -> source.onResourceCreated(testCustomResource));
        informer.start();
        await().until(() -> informer.getState() == Thread.State.BLOCKED);
        source.stop();
      }
      informer.join();
      executorServiceManager.stop(Duration.ofMillis(100));
      executorServiceManager.start(configurationService);

      source.start();
      clearInvocations(supplier);
      source.onResourceCreated(testCustomResource);

      await().untilAsserted(() -> verify(supplier, atLeast(2)).fetchResources(any()));
    } finally {
      source.stop();
      executorServiceManager.stop(Duration.ofMillis(100));
    }
  }

  @Test
  void createsAnExecutorOfItsOwnAndShutsItDownOnStop() {
    var pollingThreads = new CopyOnWriteArrayList<Thread>();
    when(supplier.fetchResources(any()))
        .thenAnswer(
            invocation -> {
              pollingThreads.add(Thread.currentThread());
              return Set.of(SampleExternalResource.testResource1());
            });
    setUpSource(eventSourceFor(new BaseConfigurationService(), null));

    source.onResourceCreated(testCustomResource);

    await().until(() -> !pollingThreads.isEmpty());
    var pollingThread = pollingThreads.get(0);
    assertThat(pollingThread.getName()).startsWith("josdk-polling-");
    assertThat(pollingThread.isDaemon()).isTrue();

    source.stop();

    await().untilAsserted(() -> assertThat(pollingThread.isAlive()).isFalse());
  }

  @Test
  void usesANewExecutorWhenStartedAfterStop() {
    setUpSource(eventSourceFor(new BaseConfigurationService(), null));
    source.onResourceCreated(testCustomResource);
    await().untilAsserted(() -> verify(supplier, atLeast(2)).fetchResources(any()));
    source.stop();
    clearInvocations(supplier);

    source.start();
    source.onResourceCreated(testCustomResource);

    await().untilAsserted(() -> verify(supplier, atLeast(2)).fetchResources(any()));
  }

  @Test
  void pollsOnTheProvidedExecutorAndLeavesItRunningOnStop() {
    var pollingThreadNames = new CopyOnWriteArrayList<String>();
    when(supplier.fetchResources(any()))
        .thenAnswer(
            invocation -> {
              pollingThreadNames.add(Thread.currentThread().getName());
              return Set.of(SampleExternalResource.testResource1());
            });
    var provided =
        Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "provided"));
    setUpSource(eventSourceFor(new BaseConfigurationService(), provided));

    try {
      source.onResourceCreated(testCustomResource);
      await().untilAsserted(() -> assertThat(pollingThreadNames).contains("provided"));

      source.stop();

      assertThat(provided.isShutdown()).isFalse();
      assertThat(pollingThreadNames).containsOnly("provided");
    } finally {
      provided.shutdownNow();
    }
  }

  @Test
  void eventSourcesDoNotShareTheirExecutor() throws Exception {
    var otherThreads = new CopyOnWriteArrayList<Thread>();
    var blockFirst = new CountDownLatch(1);
    var firstPolling = new CountDownLatch(1);
    when(supplier.fetchResources(any()))
        .thenAnswer(
            invocation -> {
              firstPolling.countDown();
              blockFirst.await();
              return Set.of(SampleExternalResource.testResource1());
            });
    @SuppressWarnings("unchecked")
    PerResourcePollingEventSource.ResourceFetcher<SampleExternalResource, TestCustomResource>
        otherSupplier = mock(PerResourcePollingEventSource.ResourceFetcher.class);
    when(otherSupplier.fetchResources(any()))
        .thenAnswer(
            invocation -> {
              otherThreads.add(Thread.currentThread());
              return Set.of(SampleExternalResource.testResource1());
            });
    var configurationService = new BaseConfigurationService();
    var first = eventSourceFor(configurationService, supplier, null);
    var second = eventSourceFor(configurationService, otherSupplier, null);
    first.setEventHandler(mock(EventHandler.class));
    second.setEventHandler(mock(EventHandler.class));

    try {
      first.start();
      second.start();
      first.onResourceCreated(testCustomResource);
      assertThat(firstPolling.await(5, TimeUnit.SECONDS)).isTrue();
      second.onResourceCreated(testCustomResource);

      // a poll of the first one hanging must not delay the polls of the second one
      await().until(() -> otherThreads.size() >= 2);
    } finally {
      blockFirst.countDown();
      first.stop();
      second.stop();
    }
  }

  @Test
  @EnabledForJreRange(min = JRE.JAVA_25)
  void pollsOnVirtualThreadsWhenEnabledInTheConfiguration() {
    var onVirtualThread = new CopyOnWriteArrayList<Boolean>();
    when(supplier.fetchResources(any()))
        .thenAnswer(
            invocation -> {
              onVirtualThread.add(isVirtual(Thread.currentThread()));
              return Set.of(SampleExternalResource.testResource1());
            });
    var configurationService =
        ConfigurationService.newOverriddenConfigurationService(
            new BaseConfigurationService(), o -> o.withUseVirtualThreads(true));
    setUpSource(eventSourceFor(configurationService, null));

    try {
      source.onResourceCreated(testCustomResource);

      await().untilAsserted(() -> assertThat(onVirtualThread).contains(true));
    } finally {
      source.stop();
    }
  }

  private PerResourcePollingEventSource<SampleExternalResource, TestCustomResource, String>
      eventSourceFor(
          ConfigurationService configurationService, ScheduledExecutorService executorService) {
    return eventSourceFor(configurationService, supplier, executorService);
  }

  private PerResourcePollingEventSource<SampleExternalResource, TestCustomResource, String>
      eventSourceFor(
          ConfigurationService configurationService,
          PerResourcePollingEventSource.ResourceFetcher<SampleExternalResource, TestCustomResource>
              fetcher,
          ScheduledExecutorService executorService) {
    var controllerConfiguration = mock(ControllerConfiguration.class);
    when(controllerConfiguration.getConfigurationService()).thenReturn(configurationService);
    when(context.getControllerConfiguration()).thenReturn(controllerConfiguration);
    return new PerResourcePollingEventSource<>(
        SampleExternalResource.class,
        context,
        new PerResourcePollingConfigurationBuilder<
                SampleExternalResource, TestCustomResource, String>(
                fetcher, Duration.ofMillis(PERIOD))
            .withResourceIDMapper(r -> r.getName() + "#" + r.getValue())
            .withExecutorService(executorService)
            .build());
  }

  private static boolean isVirtual(Thread thread) {
    try {
      return (Boolean) Thread.class.getMethod("isVirtual").invoke(thread);
    } catch (ReflectiveOperationException e) {
      return false;
    }
  }

  @Test
  void deleteEventCancelsTheScheduling() {
    when(supplier.fetchResources(any())).thenReturn(Set.of(SampleExternalResource.testResource1()));

    source.onResourceCreated(testCustomResource);

    await()
        .pollDelay(Duration.ofMillis(PERIOD))
        .atMost(Duration.ofMillis((2 * PERIOD)))
        .pollInterval(Duration.ofMillis(20))
        .untilAsserted(() -> verify(supplier, times(1)).fetchResources(any()));

    when(resourceCache.get(any())).thenReturn(Optional.empty());
    source.onResourceDeleted(testCustomResource);

    // check if not called again
    await()
        .pollDelay(Duration.ofMillis(2 * PERIOD))
        .atMost(Duration.ofMillis((4 * PERIOD)))
        .untilAsserted(() -> verify(supplier, times(1)).fetchResources(any()));
  }
}

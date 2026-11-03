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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.health.Status;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSourceTestBase;
import io.javaoperatorsdk.operator.processing.event.source.SampleExternalResource;

import static io.javaoperatorsdk.operator.processing.event.source.SampleExternalResource.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

class PollingEventSourceTest
    extends AbstractEventSourceTestBase<
        PollingEventSource<SampleExternalResource, HasMetadata, String>, EventHandler> {

  public static final int DEFAULT_WAIT_PERIOD = 100;
  public static final Duration POLL_PERIOD = Duration.ofMillis(30L);

  @SuppressWarnings("unchecked")
  private final PollingEventSource.GenericResourceFetcher<SampleExternalResource> resourceFetcher =
      mock(PollingEventSource.GenericResourceFetcher.class);

  @SuppressWarnings("deprecation")
  private final PollingEventSource<SampleExternalResource, HasMetadata, String> pollingEventSource =
      new PollingEventSource<>(
          SampleExternalResource.class,
          new PollingConfiguration<>(
              null,
              resourceFetcher,
              POLL_PERIOD,
              (SampleExternalResource er) -> er.getName() + "#" + er.getValue()));

  @BeforeEach
  public void setup() {
    setUpSource(pollingEventSource, false);
  }

  @Test
  void canBeRestartedAfterStop() throws InterruptedException {
    when(resourceFetcher.fetchResources()).thenReturn(testResponseWithTwoValues());
    pollingEventSource.start();
    Thread.sleep(DEFAULT_WAIT_PERIOD);
    pollingEventSource.stop();

    // the polling task is cancelled on stop, a new one has to be scheduled on start
    pollingEventSource.start();
    clearInvocations(resourceFetcher);

    await().untilAsserted(() -> verify(resourceFetcher, atLeast(2)).fetchResources());
    assertThat(pollingEventSource.getStatus()).isEqualTo(Status.HEALTHY);
  }

  @Test
  void stoppingDoesNotInterruptAPollInFlight() throws Exception {
    var polling = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var interrupted = new AtomicBoolean();
    var fetches = new AtomicInteger();
    when(resourceFetcher.fetchResources())
        .thenAnswer(
            invocation -> {
              if (fetches.incrementAndGet() > 1) {
                polling.countDown();
                try {
                  release.await();
                } catch (InterruptedException e) {
                  interrupted.set(true);
                }
              }
              return testResponseWithOneValue();
            });

    pollingEventSource.start();
    assertThat(polling.await(5, TimeUnit.SECONDS)).isTrue();
    pollingEventSource.stop();
    release.countDown();

    await().pollDelay(Duration.ofMillis(DEFAULT_WAIT_PERIOD)).until(() -> !interrupted.get());
  }

  @Test
  void stopCancelsThePollingTask() throws InterruptedException {
    when(resourceFetcher.fetchResources()).thenReturn(testResponseWithTwoValues());
    pollingEventSource.start();
    Thread.sleep(DEFAULT_WAIT_PERIOD);
    pollingEventSource.stop();
    clearInvocations(resourceFetcher);

    Thread.sleep(DEFAULT_WAIT_PERIOD);

    verify(resourceFetcher, never()).fetchResources();
  }

  @Test
  void pollingThreadIsADaemonSoItDoesNotKeepTheJvmAlive() throws InterruptedException {
    when(resourceFetcher.fetchResources()).thenReturn(testResponseWithTwoValues());

    var threadsBeforeStart = Thread.getAllStackTraces().keySet();

    pollingEventSource.start();
    Thread.sleep(DEFAULT_WAIT_PERIOD);

    var newPollingThreads =
        Thread.getAllStackTraces().keySet().stream()
            .filter(t -> t.getName().startsWith("josdk-polling-"))
            .filter(t -> !threadsBeforeStart.contains(t))
            .toList();

    assertThat(newPollingThreads).isNotEmpty().allMatch(Thread::isDaemon);
  }

  @Test
  void pollsAndProcessesEvents() throws InterruptedException {
    when(resourceFetcher.fetchResources()).thenReturn(testResponseWithTwoValues());
    pollingEventSource.start();
    Thread.sleep(DEFAULT_WAIT_PERIOD);

    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  void propagatesEventForRemovedResources() throws InterruptedException {
    when(resourceFetcher.fetchResources())
        .thenReturn(testResponseWithTwoValues())
        .thenReturn(testResponseWithOneValue());
    pollingEventSource.start();
    Thread.sleep(DEFAULT_WAIT_PERIOD);

    verify(eventHandler, times(3)).handleEvent(any());
  }

  @Test
  void doesNotPropagateEventIfResourceNotChanged() throws InterruptedException {
    when(resourceFetcher.fetchResources()).thenReturn(testResponseWithTwoValues());
    pollingEventSource.start();
    Thread.sleep(DEFAULT_WAIT_PERIOD);

    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  void propagatesEventOnNewResourceForPrimary() throws InterruptedException {
    when(resourceFetcher.fetchResources())
        .thenReturn(testResponseWithOneValue())
        .thenReturn(testResponseWithTwoValueForSameId());

    pollingEventSource.start();
    Thread.sleep(DEFAULT_WAIT_PERIOD);

    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  void updatesHealthIndicatorBasedOnExceptionsInFetcher() {
    when(resourceFetcher.fetchResources()).thenReturn(testResponseWithOneValue());
    pollingEventSource.start();
    assertThat(pollingEventSource.getStatus()).isEqualTo(Status.HEALTHY);

    when(resourceFetcher.fetchResources())
        // 2x - to make sure to catch the health indicator change
        .thenThrow(new RuntimeException("test exception"))
        .thenThrow(new RuntimeException("test exception"))
        .thenReturn(testResponseWithOneValue());

    await()
        .pollInterval(POLL_PERIOD)
        .untilAsserted(
            () -> assertThat(pollingEventSource.getStatus()).isEqualTo(Status.UNHEALTHY));

    await()
        .untilAsserted(() -> assertThat(pollingEventSource.getStatus()).isEqualTo(Status.HEALTHY));
  }

  @Test
  void createsAnExecutorOfItsOwnAndShutsItDownOnStop() {
    var pollingThreads = new CopyOnWriteArrayList<Thread>();
    when(resourceFetcher.fetchResources())
        .thenAnswer(
            invocation -> {
              pollingThreads.add(Thread.currentThread());
              return testResponseWithOneValue();
            });
    var eventSource = eventSourceFor(new BaseConfigurationService(), null);

    eventSource.start();

    // the initial fetch happens on the calling thread, the scheduled ones on the own executor
    await()
        .untilAsserted(
            () ->
                assertThat(pollingThreads)
                    .anyMatch(thread -> thread.getName().startsWith("josdk-polling-")));
    var pollingThread =
        pollingThreads.stream()
            .filter(thread -> thread.getName().startsWith("josdk-polling-"))
            .findFirst()
            .orElseThrow();
    assertThat(pollingThread.isDaemon()).isTrue();

    eventSource.stop();

    await().untilAsserted(() -> assertThat(pollingThread.isAlive()).isFalse());
  }

  @Test
  void usesANewExecutorWhenStartedAfterStop() {
    var pollingThreads = new CopyOnWriteArrayList<Thread>();
    when(resourceFetcher.fetchResources())
        .thenAnswer(
            invocation -> {
              pollingThreads.add(Thread.currentThread());
              return testResponseWithOneValue();
            });
    var eventSource = eventSourceFor(new BaseConfigurationService(), null);

    try {
      eventSource.start();
      await()
          .until(() -> pollingThreads.stream().anyMatch(PollingEventSourceTest::isPollingThread));
      eventSource.stop();
      pollingThreads.clear();

      eventSource.start();

      await()
          .until(() -> pollingThreads.stream().anyMatch(PollingEventSourceTest::isPollingThread));
    } finally {
      eventSource.stop();
    }
  }

  @Test
  void pollsOnTheProvidedExecutorAndLeavesItRunningOnStop() {
    var pollingThreadNames = new CopyOnWriteArrayList<String>();
    when(resourceFetcher.fetchResources())
        .thenAnswer(
            invocation -> {
              pollingThreadNames.add(Thread.currentThread().getName());
              return testResponseWithOneValue();
            });
    var provided =
        Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "provided"));
    var eventSource = eventSourceFor(new BaseConfigurationService(), provided);

    try {
      eventSource.start();
      await().untilAsserted(() -> assertThat(pollingThreadNames).contains("provided"));

      eventSource.stop();

      assertThat(provided.isShutdown()).isFalse();
      assertThat(pollingThreadNames).noneMatch(name -> name.startsWith("josdk-polling-"));
    } finally {
      provided.shutdownNow();
    }
  }

  @Test
  void eventSourcesDoNotShareTheirExecutor() {
    var firstThreads = new CopyOnWriteArrayList<Thread>();
    var secondThreads = new CopyOnWriteArrayList<Thread>();
    var blockFirst = new CountDownLatch(1);
    @SuppressWarnings("unchecked")
    PollingEventSource.GenericResourceFetcher<SampleExternalResource> slowFetcher =
        mock(PollingEventSource.GenericResourceFetcher.class);
    var fetches = new AtomicInteger();
    when(slowFetcher.fetchResources())
        .thenAnswer(
            invocation -> {
              if (fetches.incrementAndGet() > 1) {
                firstThreads.add(Thread.currentThread());
                blockFirst.await();
              }
              return testResponseWithOneValue();
            });
    when(resourceFetcher.fetchResources())
        .thenAnswer(
            invocation -> {
              secondThreads.add(Thread.currentThread());
              return testResponseWithOneValue();
            });
    var configurationService = new BaseConfigurationService();
    var first = eventSourceFor(configurationService, slowFetcher, null);
    var second = eventSourceFor(configurationService, resourceFetcher, null);

    try {
      first.start();
      await().until(() -> !firstThreads.isEmpty());
      second.start();

      // a poll of the first one hanging must not delay the polls of the second one
      await().until(() -> secondThreads.size() > 2);
      assertThat(secondThreads).doesNotContainAnyElementsOf(firstThreads);
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
    when(resourceFetcher.fetchResources())
        .thenAnswer(
            invocation -> {
              onVirtualThread.add(isVirtual(Thread.currentThread()));
              return testResponseWithOneValue();
            });
    var configurationService =
        ConfigurationService.newOverriddenConfigurationService(
            new BaseConfigurationService(), o -> o.withUseVirtualThreads(true));
    var eventSource = eventSourceFor(configurationService, null);

    try {
      eventSource.start();

      // the initial fetch happens on the calling thread, the scheduled ones on virtual threads
      await().untilAsserted(() -> assertThat(onVirtualThread).contains(true));
    } finally {
      eventSource.stop();
    }
  }

  @Test
  void stoppingDuringTheInitialFetchEndsStartCleanly() throws Exception {
    var fetching = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(resourceFetcher.fetchResources())
        .thenAnswer(
            invocation -> {
              fetching.countDown();
              release.await();
              return testResponseWithOneValue();
            });
    var eventSource = eventSourceFor(new BaseConfigurationService(), null);
    var failure = new AtomicReference<Throwable>();
    var starter =
        new Thread(
            () -> {
              try {
                eventSource.start();
              } catch (Throwable t) {
                failure.set(t);
              }
            });

    starter.start();
    assertThat(fetching.await(5, TimeUnit.SECONDS)).isTrue();
    eventSource.stop();
    release.countDown();
    starter.join(5000);

    assertThat(starter.isAlive()).isFalse();
    assertThat(failure.get()).isNull();
    clearInvocations(resourceFetcher);
    Thread.sleep(DEFAULT_WAIT_PERIOD);
    verify(resourceFetcher, never()).fetchResources();
  }

  private PollingEventSource<SampleExternalResource, HasMetadata, String> eventSourceFor(
      ConfigurationService configurationService, ScheduledExecutorService executorService) {
    return eventSourceFor(configurationService, resourceFetcher, executorService);
  }

  private PollingEventSource<SampleExternalResource, HasMetadata, String> eventSourceFor(
      ConfigurationService configurationService,
      PollingEventSource.GenericResourceFetcher<SampleExternalResource> fetcher,
      ScheduledExecutorService executorService) {
    var eventSource =
        new PollingEventSource<SampleExternalResource, HasMetadata, String>(
            SampleExternalResource.class,
            contextFor(configurationService),
            new PollingConfiguration<>(null, fetcher, POLL_PERIOD, null, executorService));
    eventSource.setEventHandler(mock(EventHandler.class));
    return eventSource;
  }

  private static boolean isPollingThread(Thread thread) {
    return thread.getName().startsWith("josdk-polling-");
  }

  private static boolean isVirtual(Thread thread) {
    try {
      return (Boolean) Thread.class.getMethod("isVirtual").invoke(thread);
    } catch (ReflectiveOperationException e) {
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  private static EventSourceContext<HasMetadata> contextFor(
      ConfigurationService configurationService) {
    var controllerConfiguration = mock(ControllerConfiguration.class);
    when(controllerConfiguration.getConfigurationService()).thenReturn(configurationService);
    var context = mock(EventSourceContext.class);
    when(context.getControllerConfiguration()).thenReturn(controllerConfiguration);
    return context;
  }

  private Map<ResourceID, Set<SampleExternalResource>> testResponseWithTwoValueForSameId() {
    Map<ResourceID, Set<SampleExternalResource>> res = new HashMap<>();
    res.put(primaryID1(), Set.of(testResource1(), testResource2()));
    return res;
  }

  private Map<ResourceID, Set<SampleExternalResource>> testResponseWithOneValue() {
    Map<ResourceID, Set<SampleExternalResource>> res = new HashMap<>();
    res.put(primaryID1(), Set.of(testResource1()));
    return res;
  }

  private Map<ResourceID, Set<SampleExternalResource>> testResponseWithTwoValues() {
    Map<ResourceID, Set<SampleExternalResource>> res = new HashMap<>();
    res.put(primaryID1(), Set.of(testResource1()));
    res.put(primaryID2(), Set.of(testResource2()));
    return res;
  }
}

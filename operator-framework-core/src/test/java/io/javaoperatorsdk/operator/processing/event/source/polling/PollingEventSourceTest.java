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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
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
        PollingEventSource<SampleExternalResource, HasMetadata>, EventHandler> {

  public static final int DEFAULT_WAIT_PERIOD = 100;
  public static final Duration POLL_PERIOD = Duration.ofMillis(30L);

  @SuppressWarnings("unchecked")
  private final PollingEventSource.GenericResourceFetcher<SampleExternalResource> resourceFetcher =
      mock(PollingEventSource.GenericResourceFetcher.class);

  private final PollingEventSource<SampleExternalResource, HasMetadata> pollingEventSource =
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
  void updatesHealthIndicatorBasedOnExceptionsInFetcher() throws InterruptedException {
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

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSourceTestBase;
import io.javaoperatorsdk.operator.processing.event.source.CacheKeyMapper;
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
        PerResourcePollingEventSource<SampleExternalResource, TestCustomResource>, EventHandler> {

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
            new PerResourcePollingConfigurationBuilder<>(supplier, Duration.ofMillis(PERIOD))
                .withCacheKeyMapper(r -> r.getName() + "#" + r.getValue())
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
  void registeringTaskOnAPredicate() {
    setUpSource(
        new PerResourcePollingEventSource<>(
            SampleExternalResource.class,
            context,
            new PerResourcePollingConfigurationBuilder<>(supplier, Duration.ofMillis(PERIOD))
                .withRegisterPredicate(
                    testCustomResource -> testCustomResource.getMetadata().getGeneration() > 1)
                .withCacheKeyMapper(CacheKeyMapper.singleResourceCacheKeyMapper())
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

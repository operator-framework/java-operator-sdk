package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSourceTestBase;
import io.javaoperatorsdk.operator.processing.event.source.Cache;
import io.javaoperatorsdk.operator.processing.event.source.CacheKeyMapper;
import io.javaoperatorsdk.operator.processing.event.source.SampleExternalResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PerResourcePollingEventSourceTest extends
    AbstractEventSourceTestBase<PerResourcePollingEventSource<SampleExternalResource, TestCustomResource>, EventHandler> {

  public static final int PERIOD = 150;
  private PerResourcePollingEventSource.ResourceFetcher<SampleExternalResource, TestCustomResource> supplier =
      mock(PerResourcePollingEventSource.ResourceFetcher.class);
  private Cache<TestCustomResource> resourceCache = mock(Cache.class);
  private TestCustomResource testCustomResource = TestUtils.testCustomResource();

  @BeforeEach
  public void setup() {
    when(resourceCache.get(any())).thenReturn(Optional.of(testCustomResource));
    when(supplier.fetchResources(any()))
        .thenReturn(Set.of(SampleExternalResource.testResource1()));

    setUpSource(new PerResourcePollingEventSource<>(supplier, resourceCache, PERIOD,
        SampleExternalResource.class, r -> r.getName() + "#" + r.getValue()));
  }

  @Test
  void pollsTheResourceAfterAwareOfIt() throws InterruptedException {
    source.onResourceCreated(testCustomResource);

    Thread.sleep(3 * PERIOD);
    verify(supplier, atLeast(2)).fetchResources(eq(testCustomResource));
    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  void registeringTaskOnAPredicate() throws InterruptedException {
    setUpSource(new PerResourcePollingEventSource<>(supplier, resourceCache, PERIOD,
        testCustomResource -> testCustomResource.getMetadata().getGeneration() > 1,
        SampleExternalResource.class, CacheKeyMapper.singleResourceCacheKeyMapper()));
    source.onResourceCreated(testCustomResource);
    Thread.sleep(2 * PERIOD);

    verify(supplier, times(0)).fetchResources(eq(testCustomResource));
    testCustomResource.getMetadata().setGeneration(2L);
    source.onResourceUpdated(testCustomResource, testCustomResource);

    Thread.sleep(2 * PERIOD);

    verify(supplier, atLeast(1)).fetchResources(eq(testCustomResource));
  }

  @Test
  void propagateEventOnDeletedResource() throws InterruptedException {
    source.onResourceCreated(testCustomResource);
    when(supplier.fetchResources(any()))
        .thenReturn(Set.of(SampleExternalResource.testResource1()))
        .thenReturn(Collections.emptySet());

    Thread.sleep(3 * PERIOD);
    verify(supplier, atLeast(2)).fetchResources(eq(testCustomResource));
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  void getSecondaryResourceInitiatesFetchJustForFirstTime() throws InterruptedException {
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

    Thread.sleep(PERIOD * 2);

    verify(supplier, atLeast(2)).fetchResources(eq(testCustomResource));
    value = source.getSecondaryResources(testCustomResource);
    assertThat(value).hasSize(2);
  }

  @Test
  void getsValueFromCacheOrSupplier() throws InterruptedException {
    source.onResourceCreated(testCustomResource);
    when(supplier.fetchResources(any()))
        .thenReturn(Collections.emptySet())
        .thenReturn(Set.of(SampleExternalResource.testResource1()));

    Thread.sleep(PERIOD / 3);

    var value = source.getSecondaryResources(testCustomResource);
    verify(eventHandler, times(0)).handleEvent(any());
    assertThat(value).isEmpty();

    Thread.sleep(PERIOD * 2);

    value = source.getSecondaryResources(testCustomResource);
    assertThat(value).hasSize(1);
    verify(eventHandler, times(1)).handleEvent(any());
  }

}

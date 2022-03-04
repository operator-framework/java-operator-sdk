package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSourceTestBase;
import io.javaoperatorsdk.operator.processing.event.source.Cache;
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

  public static final int PERIOD = 80;
  private PerResourcePollingEventSource.ResourceFetcher<SampleExternalResource, TestCustomResource> supplier =
      mock(PerResourcePollingEventSource.ResourceFetcher.class);
  private Cache<TestCustomResource> resourceCache = mock(Cache.class);
  private TestCustomResource testCustomResource = TestUtils.testCustomResource();

  @BeforeEach
  public void setup() {
    when(resourceCache.get(any())).thenReturn(Optional.of(testCustomResource));
    when(supplier.fetchResource(any()))
        .thenReturn(Optional.of(SampleExternalResource.testResource1()));

    setUpSource(new PerResourcePollingEventSource<>(supplier, resourceCache, PERIOD,
        SampleExternalResource.class));
  }

  @Test
  public void pollsTheResourceAfterAwareOfIt() throws InterruptedException {
    source.onResourceCreated(testCustomResource);

    Thread.sleep(3 * PERIOD);
    verify(supplier, atLeast(2)).fetchResource(eq(testCustomResource));
    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  public void registeringTaskOnAPredicate() throws InterruptedException {
    setUpSource(new PerResourcePollingEventSource<>(supplier, resourceCache, PERIOD,
        testCustomResource -> testCustomResource.getMetadata().getGeneration() > 1,
        SampleExternalResource.class));
    source.onResourceCreated(testCustomResource);
    Thread.sleep(2 * PERIOD);

    verify(supplier, times(0)).fetchResource(eq(testCustomResource));
    testCustomResource.getMetadata().setGeneration(2L);
    source.onResourceUpdated(testCustomResource, testCustomResource);

    Thread.sleep(2 * PERIOD);

    verify(supplier, atLeast(1)).fetchResource(eq(testCustomResource));
  }

  @Test
  public void propagateEventOnDeletedResource() throws InterruptedException {
    source.onResourceCreated(testCustomResource);
    when(supplier.fetchResource(any()))
        .thenReturn(Optional.of(SampleExternalResource.testResource1()))
        .thenReturn(Optional.empty());

    Thread.sleep(3 * PERIOD);
    verify(supplier, atLeast(2)).fetchResource(eq(testCustomResource));
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  public void getsValueFromCacheOrSupplier() throws InterruptedException {
    source.onResourceCreated(testCustomResource);
    when(supplier.fetchResource(any()))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(SampleExternalResource.testResource1()));

    Thread.sleep(PERIOD / 2);

    var value = source.getValueFromCacheOrSupplier(ResourceID.fromResource(testCustomResource));

    Thread.sleep(PERIOD * 2);

    assertThat(value).isPresent();
    verify(eventHandler, never()).handleEvent(any());
  }

}

package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.util.Optional;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceRegistry;
import io.javaoperatorsdk.operator.processing.event.source.SampleExternalResource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceCache;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PerResourcePollingEventSourceTest {

  public static final int PERIOD = 80;
  private PerResourcePollingEventSource<SampleExternalResource, TestCustomResource> pollingEventSource;
  private final PerResourcePollingEventSource.ResourceSupplier<SampleExternalResource, TestCustomResource> supplier =
      mock(PerResourcePollingEventSource.ResourceSupplier.class);
  private final ResourceCache<TestCustomResource> resourceCache = mock(ResourceCache.class);
  private Cache<ResourceID, SampleExternalResource> cache;
  private final EventHandler eventHandler = mock(EventHandler.class);
  private final TestCustomResource testCustomResource = TestUtils.testCustomResource();

  @BeforeEach
  public void setup() {
    CachingProvider cachingProvider = new CaffeineCachingProvider();
    CacheManager cacheManager = cachingProvider.getCacheManager();
    cache = cacheManager.createCache("test-caching", new MutableConfiguration<>());

    when(resourceCache.get(any())).thenReturn(Optional.of(testCustomResource));
    when(supplier.getResources(any()))
        .thenReturn(Optional.of(SampleExternalResource.testResource1()));

    pollingEventSource =
        new PerResourcePollingEventSource<>(supplier, resourceCache, PERIOD, cache,
            TestCustomResource.class);
    EventSourceRegistry registry = mock(EventSourceRegistry.class);
    when(registry.getEventHandler()).thenReturn(eventHandler);
    pollingEventSource.setEventRegistry(registry);
  }

  @Test
  public void pollsTheResourceAfterAwareOfIt() throws InterruptedException {
    pollingEventSource.start();
    pollingEventSource.onResourceCreated(testCustomResource);

    Thread.sleep(3 * PERIOD);
    verify(supplier, atLeast(2)).getResources(eq(testCustomResource));
    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  public void registeringTaskOnAPredicate() throws InterruptedException {
    pollingEventSource = new PerResourcePollingEventSource<>(supplier, resourceCache, PERIOD, cache,
        testCustomResource -> testCustomResource.getMetadata().getGeneration() > 1,
        TestCustomResource.class);
    EventSourceRegistry registry = mock(EventSourceRegistry.class);
    when(registry.getEventHandler()).thenReturn(eventHandler);
    pollingEventSource.setEventRegistry(registry);
    pollingEventSource.start();
    pollingEventSource.onResourceCreated(testCustomResource);
    Thread.sleep(2 * PERIOD);

    verify(supplier, times(0)).getResources(eq(testCustomResource));
    testCustomResource.getMetadata().setGeneration(2L);
    pollingEventSource.onResourceUpdated(testCustomResource, testCustomResource);

    Thread.sleep(2 * PERIOD);

    verify(supplier, atLeast(1)).getResources(eq(testCustomResource));
  }

  @Test
  public void propagateEventOnDeletedResource() throws InterruptedException {
    pollingEventSource.start();
    pollingEventSource.onResourceCreated(testCustomResource);
    when(supplier.getResources(any()))
        .thenReturn(Optional.of(SampleExternalResource.testResource1()))
        .thenReturn(Optional.empty());

    Thread.sleep(3 * PERIOD);
    verify(supplier, atLeast(2)).getResources(eq(testCustomResource));
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  public void getsValueFromCacheOrSupplier() throws InterruptedException {
    pollingEventSource.start();
    pollingEventSource.onResourceCreated(testCustomResource);
    when(supplier.getResources(any()))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(SampleExternalResource.testResource1()));

    Thread.sleep(PERIOD / 2);

    var value =
        pollingEventSource.getValueFromCacheOrSupplier(ResourceID.fromResource(testCustomResource));

    Thread.sleep(PERIOD * 2);

    assertThat(value).isPresent();
    verify(eventHandler, never()).handleEvent(any());
  }

}

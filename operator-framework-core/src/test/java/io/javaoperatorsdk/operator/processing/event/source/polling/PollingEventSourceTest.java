package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceRegistry;
import io.javaoperatorsdk.operator.processing.event.source.SampleExternalResource;

import com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider;

import static io.javaoperatorsdk.operator.processing.event.source.SampleExternalResource.*;
import static org.mockito.Mockito.*;

class PollingEventSourceTest {

  private PollingEventSource<SampleExternalResource, HasMetadata> pollingEventSource;
  private final Supplier<Map<ResourceID, SampleExternalResource>> supplier = mock(Supplier.class);
  private final EventHandler eventHandler = mock(EventHandler.class);

  @BeforeEach
  public void setup() {
    CachingProvider cachingProvider = new CaffeineCachingProvider();
    CacheManager cacheManager = cachingProvider.getCacheManager();
    Cache<ResourceID, SampleExternalResource> cache = cacheManager.createCache("test-caching",
        new MutableConfiguration<>());

    pollingEventSource = new PollingEventSource<>(supplier, 50, cache, HasMetadata.class);
    EventSourceRegistry registry = mock(EventSourceRegistry.class);
    when(registry.getEventHandler()).thenReturn(eventHandler);
    pollingEventSource.setEventRegistry(registry);
    pollingEventSource.start();
  }

  @AfterEach
  public void teardown() {
    pollingEventSource.stop();
  }

  @Test
  public void pollsAndProcessesEvents() throws InterruptedException {
    when(supplier.get()).thenReturn(testResponseWithTwoValues());
    pollingEventSource.start();

    Thread.sleep(100);

    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  public void propagatesEventForRemovedResources() throws InterruptedException {
    when(supplier.get()).thenReturn(testResponseWithTwoValues())
        .thenReturn(testResponseWithOneValue());
    pollingEventSource.start();

    Thread.sleep(150);

    verify(eventHandler, times(3)).handleEvent(any());
  }

  @Test
  public void doesNotPropagateEventIfResourceNotChanged() throws InterruptedException {
    when(supplier.get()).thenReturn(testResponseWithTwoValues());
    pollingEventSource.start();

    Thread.sleep(250);

    verify(eventHandler, times(2)).handleEvent(any());
  }

  private Map<ResourceID, SampleExternalResource> testResponseWithOneValue() {
    Map<ResourceID, SampleExternalResource> res = new HashMap<>();
    res.put(testResource1ID(), testResource1());
    return res;
  }

  private Map<ResourceID, SampleExternalResource> testResponseWithTwoValues() {
    Map<ResourceID, SampleExternalResource> res = new HashMap<>();
    res.put(testResource1ID(), testResource1());
    res.put(testResource2ID(), testResource2());
    return res;
  }

}

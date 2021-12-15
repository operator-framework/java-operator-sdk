package io.javaoperatorsdk.operator.processing.event.source;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider;

import static io.javaoperatorsdk.operator.processing.event.source.SampleExternalResource.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CachingEventSourceTest {

  private CachingEventSource<SampleExternalResource, HasMetadata> cachingEventSource;
  private final EventHandler eventHandler = mock(EventHandler.class);

  @BeforeEach
  public void setup() {
    CachingProvider cachingProvider = new CaffeineCachingProvider();
    CacheManager cacheManager = cachingProvider.getCacheManager();
    Cache<ResourceID, SampleExternalResource> cache = cacheManager.createCache("test-caching",
        new MutableConfiguration<>());

    cachingEventSource = new SimpleCachingEventSource(cache);
    EventSourceRegistry registry = mock(EventSourceRegistry.class);
    when(registry.getEventHandler()).thenReturn(eventHandler);
    cachingEventSource.setEventRegistry(registry);
    cachingEventSource.start();
  }

  @AfterEach
  public void tearDown() {
    cachingEventSource.stop();
  }

  @Test
  public void putsNewResourceIntoCacheAndProducesEvent() {
    cachingEventSource.handleEvent(testResource1(), testResource1ID());

    verify(eventHandler, times(1)).handleEvent(eq(new Event(testResource1ID())));
    assertThat(cachingEventSource.getCachedValue(testResource1ID())).isPresent();
  }

  @Test
  public void propagatesEventIfResourceChanged() {
    var res2 = testResource1();
    res2.setValue("changedValue");
    cachingEventSource.handleEvent(testResource1(), testResource1ID());
    cachingEventSource.handleEvent(res2, testResource1ID());


    verify(eventHandler, times(2)).handleEvent(eq(new Event(testResource1ID())));
    assertThat(cachingEventSource.getCachedValue(testResource1ID()).get()).isEqualTo(res2);
  }

  @Test
  public void noEventPropagatedIfTheResourceIsNotChanged() {
    cachingEventSource.handleEvent(testResource1(), testResource1ID());
    cachingEventSource.handleEvent(testResource1(), testResource1ID());

    verify(eventHandler, times(1)).handleEvent(eq(new Event(testResource1ID())));
    assertThat(cachingEventSource.getCachedValue(testResource1ID())).isPresent();
  }

  @Test
  public void propagatesEventOnDeleteIfThereIsPrevResourceInCache() {
    cachingEventSource.handleEvent(testResource1(), testResource1ID());
    cachingEventSource.handleDelete(testResource1ID());

    verify(eventHandler, times(2)).handleEvent(eq(new Event(testResource1ID())));
    assertThat(cachingEventSource.getCachedValue(testResource1ID())).isNotPresent();
  }

  @Test
  public void noEventOnDeleteIfResourceWasNotInCacheBefore() {
    cachingEventSource.handleDelete(testResource1ID());

    verify(eventHandler, times(0)).handleEvent(eq(new Event(testResource1ID())));
  }


  public static class SimpleCachingEventSource
      extends CachingEventSource<SampleExternalResource, HasMetadata> {
    public SimpleCachingEventSource(Cache<ResourceID, SampleExternalResource> cache) {
      super(cache, HasMetadata.class);
    }
  }

}

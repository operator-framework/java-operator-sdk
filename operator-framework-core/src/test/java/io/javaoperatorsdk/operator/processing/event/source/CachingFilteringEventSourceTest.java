package io.javaoperatorsdk.operator.processing.event.source;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider;

import static io.javaoperatorsdk.operator.processing.event.source.SampleExternalResource.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CachingFilteringEventSourceTest {

  private CachingFilteringEventSource<SampleExternalResource> cachingFilteringEventSource;
  private Cache<ResourceID, SampleExternalResource> cache;
  private EventHandler eventHandlerMock = mock(EventHandler.class);

  @BeforeEach
  public void setup() {
    CachingProvider cachingProvider = new CaffeineCachingProvider();
    CacheManager cacheManager = cachingProvider.getCacheManager();
    cache = cacheManager.createCache("test-caching", new MutableConfiguration<>());

    cachingFilteringEventSource = new SimpleCachingFilteringEventSource(cache, null);
    cachingFilteringEventSource.setEventHandler(eventHandlerMock);
    cachingFilteringEventSource.start();
  }

  @AfterEach
  public void tearDown() {
    cachingFilteringEventSource.stop();
  }

  @Test
  public void putsNewResourceIntoCacheAndProducesEvent() {
    cachingFilteringEventSource.handleEvent(testResource1(), testResource1ID());

    verify(eventHandlerMock, times(1)).handleEvent(eq(new Event(testResource1ID())));
    assertThat(cachingFilteringEventSource.getCachedValue(testResource1ID())).isPresent();
  }

  @Test
  public void propagatesEventIfResourceChanged() {
    var res2 = testResource1();
    res2.setValue("changedValue");
    cachingFilteringEventSource.handleEvent(testResource1(), testResource1ID());
    cachingFilteringEventSource.handleEvent(res2, testResource1ID());


    verify(eventHandlerMock, times(2)).handleEvent(eq(new Event(testResource1ID())));
    assertThat(cachingFilteringEventSource.getCachedValue(testResource1ID()).get()).isEqualTo(res2);
  }

  @Test
  public void noEventPropagatedIfTheResourceIsNotChanged() {
    cachingFilteringEventSource.handleEvent(testResource1(), testResource1ID());
    cachingFilteringEventSource.handleEvent(testResource1(), testResource1ID());

    verify(eventHandlerMock, times(1)).handleEvent(eq(new Event(testResource1ID())));
    assertThat(cachingFilteringEventSource.getCachedValue(testResource1ID())).isPresent();
  }

  @Test
  public void supportFilteringEvents() {
    cachingFilteringEventSource = new SimpleCachingFilteringEventSource(cache,
        (newValue, oldValue, relatedResourceID) -> !newValue.getValue().equals(DEFAULT_VALUE_1));
    cachingFilteringEventSource.setEventHandler(eventHandlerMock);
    cachingFilteringEventSource.start();

    var res2 = testResource1();
    res2.setValue("changedValue");
    cachingFilteringEventSource.handleEvent(testResource1(), testResource1ID());
    cachingFilteringEventSource.handleEvent(res2, testResource1ID());


    verify(eventHandlerMock, times(1)).handleEvent(eq(new Event(testResource1ID())));
    assertThat(cachingFilteringEventSource.getCachedValue(testResource1ID()).get()).isEqualTo(res2);
  }

  @Test
  public void propagatesEventOnDeleteIfThereIsPrevResourceInCache() {
    cachingFilteringEventSource.handleEvent(testResource1(), testResource1ID());
    cachingFilteringEventSource.handleDelete(testResource1ID());

    verify(eventHandlerMock, times(2)).handleEvent(eq(new Event(testResource1ID())));
    assertThat(cachingFilteringEventSource.getCachedValue(testResource1ID())).isNotPresent();
  }

  @Test
  public void noEventOnDeleteIfResourceWasNotInCacheBefore() {
    cachingFilteringEventSource.handleDelete(testResource1ID());

    verify(eventHandlerMock, times(0)).handleEvent(eq(new Event(testResource1ID())));
  }

  @Test
  public void deleteSupportsFiltering() {
    cachingFilteringEventSource = new SimpleCachingFilteringEventSource(cache,
        (newValue, oldValue, relatedResourceID) -> !newValue.getValue().equals(DEFAULT_VALUE_1));
    cachingFilteringEventSource.setEventHandler(eventHandlerMock);
    cachingFilteringEventSource.start();

    cachingFilteringEventSource.handleEvent(testResource1(), testResource1ID());
    cachingFilteringEventSource.handleDelete(testResource1ID());

    verify(eventHandlerMock, times(1)).handleEvent(eq(new Event(testResource1ID())));
  }
  
  public static class SimpleCachingFilteringEventSource
      extends CachingFilteringEventSource<SampleExternalResource> {
    public SimpleCachingFilteringEventSource(Cache<ResourceID, SampleExternalResource> cache,
        EventFilter<SampleExternalResource> eventFilter) {
      super(cache, eventFilter);
    }
  }
  
}

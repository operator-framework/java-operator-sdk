package io.javaoperatorsdk.operator.processing.event.source;

import java.io.Serializable;
import java.util.Objects;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CachingFilteringEventSourceTest {

  public static final String DEFAULT_VALUE = "value";
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
    cachingFilteringEventSource.handleEvent(testResource(), testResourceID());

    verify(eventHandlerMock, times(1)).handleEvent(eq(new Event(testResourceID())));
    assertThat(cachingFilteringEventSource.getCachedValue(testResourceID())).isPresent();
  }

  @Test
  public void propagatesEventIfResourceChanged() {
    var res2 = testResource();
    res2.setValue("changedValue");
    cachingFilteringEventSource.handleEvent(testResource(), testResourceID());
    cachingFilteringEventSource.handleEvent(res2, testResourceID());


    verify(eventHandlerMock, times(2)).handleEvent(eq(new Event(testResourceID())));
    assertThat(cachingFilteringEventSource.getCachedValue(testResourceID()).get()).isEqualTo(res2);
  }

  @Test
  public void noEventPropagatedIfTheResourceIsNotChanged() {
    cachingFilteringEventSource.handleEvent(testResource(), testResourceID());
    cachingFilteringEventSource.handleEvent(testResource(), testResourceID());

    verify(eventHandlerMock, times(1)).handleEvent(eq(new Event(testResourceID())));
    assertThat(cachingFilteringEventSource.getCachedValue(testResourceID())).isPresent();
  }

  @Test
  public void supportFilteringEvents() {
    cachingFilteringEventSource = new SimpleCachingFilteringEventSource(cache,
        (newValue, oldValue, relatedResourceID) -> !newValue.getValue().equals(DEFAULT_VALUE));
    cachingFilteringEventSource.setEventHandler(eventHandlerMock);
    cachingFilteringEventSource.start();

    var res2 = testResource();
    res2.setValue("changedValue");
    cachingFilteringEventSource.handleEvent(testResource(), testResourceID());
    cachingFilteringEventSource.handleEvent(res2, testResourceID());


    verify(eventHandlerMock, times(1)).handleEvent(eq(new Event(testResourceID())));
    assertThat(cachingFilteringEventSource.getCachedValue(testResourceID()).get()).isEqualTo(res2);
  }

  @Test
  public void propagatesEventOnDeleteIfThereIsPrevResourceInCache() {
    cachingFilteringEventSource.handleEvent(testResource(), testResourceID());
    cachingFilteringEventSource.handleDelete(testResourceID());

    verify(eventHandlerMock, times(2)).handleEvent(eq(new Event(testResourceID())));
    assertThat(cachingFilteringEventSource.getCachedValue(testResourceID())).isNotPresent();
  }

  @Test
  public void noEventOnDeleteIfResourceWasNotInCacheBefore() {
    cachingFilteringEventSource.handleDelete(testResourceID());

    verify(eventHandlerMock, times(0)).handleEvent(eq(new Event(testResourceID())));
  }

  @Test
  public void deleteSupportsFiltering() {
    cachingFilteringEventSource = new SimpleCachingFilteringEventSource(cache,
        (newValue, oldValue, relatedResourceID) -> !newValue.getValue().equals(DEFAULT_VALUE));
    cachingFilteringEventSource.setEventHandler(eventHandlerMock);
    cachingFilteringEventSource.start();

    cachingFilteringEventSource.handleEvent(testResource(), testResourceID());
    cachingFilteringEventSource.handleDelete(testResourceID());

    verify(eventHandlerMock, times(1)).handleEvent(eq(new Event(testResourceID())));
  }

  private ResourceID testResourceID() {
    return new ResourceID("name1", "test-namespace");
  }

  private SampleExternalResource testResource() {
    return new SampleExternalResource("name1", DEFAULT_VALUE);
  }

  public static class SimpleCachingFilteringEventSource
      extends CachingFilteringEventSource<SampleExternalResource> {
    public SimpleCachingFilteringEventSource(Cache<ResourceID, SampleExternalResource> cache,
        EventFilter<SampleExternalResource> eventFilter) {
      super(cache, eventFilter);
    }
  }

  public static class SampleExternalResource implements Serializable {
    private String name;
    private String value;

    public SampleExternalResource(String name, String value) {
      this.name = name;
      this.value = value;
    }

    public String getName() {
      return name;
    }

    public SampleExternalResource setName(String name) {
      this.name = name;
      return this;
    }

    public String getValue() {
      return value;
    }

    public SampleExternalResource setValue(String value) {
      this.value = value;
      return this;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;
      SampleExternalResource that = (SampleExternalResource) o;
      return Objects.equals(name, that.name) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, value);
    }

  }

}

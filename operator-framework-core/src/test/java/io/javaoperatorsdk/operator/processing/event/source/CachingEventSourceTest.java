package io.javaoperatorsdk.operator.processing.event.source;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventHandler;

import static io.javaoperatorsdk.operator.processing.event.source.SampleExternalResource.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CachingEventSourceTest extends
    AbstractEventSourceTestBase<CachingEventSource<SampleExternalResource, HasMetadata>, EventHandler> {

  @BeforeEach
  public void setup() {
    setUpSource(new SimpleCachingEventSource());
  }

  @Test
  public void putsNewResourceIntoCacheAndProducesEvent() {
    source.handleEvent(testResource1(), testResource1ID());

    verify(eventHandler, times(1)).handleEvent(eq(new Event(testResource1ID())));
    assertThat(source.getCachedValue(testResource1ID())).isPresent();
  }

  @Test
  public void propagatesEventIfResourceChanged() {
    var res2 = testResource1();
    res2.setValue("changedValue");
    source.handleEvent(testResource1(), testResource1ID());
    source.handleEvent(res2, testResource1ID());


    verify(eventHandler, times(2)).handleEvent(eq(new Event(testResource1ID())));
    assertThat(source.getCachedValue(testResource1ID()).get()).isEqualTo(res2);
  }

  @Test
  public void noEventPropagatedIfTheResourceIsNotChanged() {
    source.handleEvent(testResource1(), testResource1ID());
    source.handleEvent(testResource1(), testResource1ID());

    verify(eventHandler, times(1)).handleEvent(eq(new Event(testResource1ID())));
    assertThat(source.getCachedValue(testResource1ID())).isPresent();
  }

  @Test
  public void propagatesEventOnDeleteIfThereIsPrevResourceInCache() {
    source.handleEvent(testResource1(), testResource1ID());
    source.handleDelete(testResource1ID());

    verify(eventHandler, times(2)).handleEvent(eq(new Event(testResource1ID())));
    assertThat(source.getCachedValue(testResource1ID())).isNotPresent();
  }

  @Test
  public void noEventOnDeleteIfResourceWasNotInCacheBefore() {
    source.handleDelete(testResource1ID());

    verify(eventHandler, times(0)).handleEvent(eq(new Event(testResource1ID())));
  }


  public static class SimpleCachingEventSource
      extends CachingEventSource<SampleExternalResource, HasMetadata> {
    public SimpleCachingEventSource() {
      super(SampleExternalResource.class);
    }
  }

}

package io.javaoperatorsdk.operator.processing.event.source;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import static io.javaoperatorsdk.operator.processing.event.source.SampleExternalResource.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ExternalResourceCachingEventSourceTest extends
    AbstractEventSourceTestBase<ExternalResourceCachingEventSource<SampleExternalResource, HasMetadata>, EventHandler> {

  private ResourceID primary = new ResourceID("test1", "default");

  @BeforeEach
  public void setup() {
    setUpSource(new TestExternalCachingEventSource());
  }

  @Test
  public void putsNewResourceIntoCacheAndProducesEvent() {
    source.handleResourcesUpdate(primaryID1(), testResource1());

    verify(eventHandler, times(1)).handleEvent(eq(new Event(primaryID1())));
    assertThat(source.getSecondaryResource(primaryID1())).isPresent();
  }

  @Test
  public void propagatesEventIfResourceChanged() {
    var res2 = testResource1();
    res2.setValue("changedValue");
    source.handleResourcesUpdate(primaryID1(), testResource1());
    source.handleResourcesUpdate(primaryID1(), res2);


    verify(eventHandler, times(2)).handleEvent(eq(new Event(primaryID1())));
    assertThat(source.getSecondaryResource(primaryID1()).get()).isEqualTo(res2);
  }

  @Test
  public void noEventPropagatedIfTheResourceIsNotChanged() {
    source.handleResourcesUpdate(primaryID1(), testResource1());
    source.handleResourcesUpdate(primaryID1(), testResource1());

    verify(eventHandler, times(1)).handleEvent(eq(new Event(primaryID1())));
    assertThat(source.getSecondaryResource(primaryID1())).isPresent();
  }

  @Test
  public void propagatesEventOnDeleteIfThereIsPrevResourceInCache() {
    source.handleResourcesUpdate(primaryID1(), testResource1());
    source.handleDelete(primaryID1());

    verify(eventHandler, times(2)).handleEvent(eq(new Event(primaryID1())));
    assertThat(source.getSecondaryResource(primaryID1())).isNotPresent();
  }

  @Test
  public void noEventOnDeleteIfResourceWasNotInCacheBefore() {
    source.handleDelete(primaryID1());

    verify(eventHandler, times(0)).handleEvent(eq(new Event(primaryID1())));
  }


  public static class TestExternalCachingEventSource
      extends ExternalResourceCachingEventSource<SampleExternalResource, HasMetadata> {
    public TestExternalCachingEventSource() {
      super(SampleExternalResource.class, (r) -> r.getName() + "#" + r.getValue());
    }
  }

}

package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventHandler;

import static io.javaoperatorsdk.operator.processing.event.source.SampleExternalResource.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ExternalResourceCachingEventSourceTest extends
    AbstractEventSourceTestBase<ExternalResourceCachingEventSource<SampleExternalResource, HasMetadata>, EventHandler> {

  @BeforeEach
  public void setup() {
    setUpSource(new TestExternalCachingEventSource());
  }

  @Test
  void putsNewResourceIntoCacheAndProducesEvent() {
    source.handleResourcesUpdate(primaryID1(), testResource1());

    verify(eventHandler, times(1)).handleEvent(new Event(primaryID1()));
    assertThat(source.getSecondaryResource(primaryID1())).isPresent();
  }

  @Test
  void propagatesEventIfResourceChanged() {
    var res2 = testResource1();
    res2.setValue("changedValue");
    source.handleResourcesUpdate(primaryID1(), testResource1());
    source.handleResourcesUpdate(primaryID1(), res2);

    verify(eventHandler, times(2)).handleEvent(new Event(primaryID1()));
    assertThat(source.getSecondaryResource(primaryID1())).contains(res2);
  }

  @Test
  void noEventPropagatedIfTheResourceIsNotChanged() {
    source.handleResourcesUpdate(primaryID1(), testResource1());
    source.handleResourcesUpdate(primaryID1(), testResource1());

    verify(eventHandler, times(1)).handleEvent(new Event(primaryID1()));
    assertThat(source.getSecondaryResource(primaryID1())).isPresent();
  }

  @Test
  void propagatesEventOnDeleteIfThereIsPrevResourceInCache() {
    source.handleResourcesUpdate(primaryID1(), testResource1());
    source.handleDelete(primaryID1());

    verify(eventHandler, times(2)).handleEvent(new Event(primaryID1()));
    assertThat(source.getSecondaryResource(primaryID1())).isNotPresent();
  }

  @Test
  void noEventOnDeleteIfResourceWasNotInCacheBefore() {
    source.handleDelete(primaryID1());

    verify(eventHandler, times(0)).handleEvent(new Event(primaryID1()));
  }

  @Test
  void handleMultipleResourceTrivialCase() {
    source.handleResourcesUpdate(primaryID1(), Set.of(testResource1(), testResource2()));

    verify(eventHandler, times(1)).handleEvent(new Event(primaryID1()));
    assertThat(source.getSecondaryResources(primaryID1()))
        .containsExactlyInAnyOrder(testResource1(), testResource2());
  }

  @Test
  void handleOneResourceRemovedFromMultiple() {
    source.handleResourcesUpdate(primaryID1(), Set.of(testResource1(), testResource2()));
    source.handleResourcesUpdate(primaryID1(), Set.of(testResource1()));

    verify(eventHandler, times(2)).handleEvent(new Event(primaryID1()));
    assertThat(source.getSecondaryResources(primaryID1())).containsExactly(testResource1());
  }

  @Test
  void addingAdditionalResource() {
    source.handleResourcesUpdate(primaryID1(), Set.of(testResource1()));
    source.handleResourcesUpdate(primaryID1(), Set.of(testResource1(), testResource2()));

    verify(eventHandler, times(2)).handleEvent(new Event(primaryID1()));
    assertThat(source.getSecondaryResources(primaryID1()))
        .containsExactlyInAnyOrder(testResource1(), testResource2());
  }

  @Test
  void replacingResource() {
    source.handleResourcesUpdate(primaryID1(), Set.of(testResource1()));
    source.handleResourcesUpdate(primaryID1(), Set.of(testResource2()));

    verify(eventHandler, times(2)).handleEvent(new Event(primaryID1()));
    assertThat(source.getSecondaryResources(primaryID1())).containsExactly(testResource2());
  }

  @Test
  void handlesDeleteFromMultipleResources() {
    source.handleResourcesUpdate(primaryID1(), Set.of(testResource1(), testResource2()));
    source.handleDelete(primaryID1(), testResource1());

    verify(eventHandler, times(2)).handleEvent(new Event(primaryID1()));
    assertThat(source.getSecondaryResources(primaryID1())).containsExactly(testResource2());
  }

  @Test
  void handlesDeleteAllFromMultipleResources() {
    source.handleResourcesUpdate(primaryID1(), Set.of(testResource1(), testResource2()));
    source.handleDeleteResources(primaryID1(), Set.of(testResource1(), testResource2()));

    verify(eventHandler, times(2)).handleEvent(new Event(primaryID1()));
    assertThat(source.getSecondaryResources(primaryID1())).isEmpty();
  }

  public static class TestExternalCachingEventSource
      extends ExternalResourceCachingEventSource<SampleExternalResource, HasMetadata> {
    public TestExternalCachingEventSource() {
      super(SampleExternalResource.class, (r) -> r.getName() + "#" + r.getValue());
    }
  }

}

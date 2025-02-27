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

class ExternalResourceCachingEventSourceTest
    extends AbstractEventSourceTestBase<
        ExternalResourceCachingEventSource<SampleExternalResource, HasMetadata>, EventHandler> {

  @BeforeEach
  public void setup() {
    setUpSource(new TestExternalCachingEventSource());
  }

  @Test
  void putsNewResourceIntoCacheAndProducesEvent() {
    source.handleResources(primaryID1(), testResource1());

    verify(eventHandler, times(1)).handleEvent(new Event(primaryID1()));
    assertThat(source.getSecondaryResource(primaryID1())).isPresent();
  }

  @Test
  void propagatesEventIfResourceChanged() {
    var res2 = testResource1();
    res2.setValue("changedValue");
    source.handleResources(primaryID1(), testResource1());
    source.handleResources(primaryID1(), res2);

    verify(eventHandler, times(2)).handleEvent(new Event(primaryID1()));
    assertThat(source.getSecondaryResource(primaryID1())).contains(res2);
  }

  @Test
  void noEventPropagatedIfTheResourceIsNotChanged() {
    source.handleResources(primaryID1(), testResource1());
    source.handleResources(primaryID1(), testResource1());

    verify(eventHandler, times(1)).handleEvent(new Event(primaryID1()));
    assertThat(source.getSecondaryResource(primaryID1())).isPresent();
  }

  @Test
  void propagatesEventOnDeleteIfThereIsPrevResourceInCache() {
    source.handleResources(primaryID1(), testResource1());
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
    source.handleResources(primaryID1(), Set.of(testResource1(), testResource2()));

    verify(eventHandler, times(1)).handleEvent(new Event(primaryID1()));
    assertThat(source.getSecondaryResources(primaryID1()))
        .containsExactlyInAnyOrder(testResource1(), testResource2());
  }

  @Test
  void handleOneResourceRemovedFromMultiple() {
    source.handleResources(primaryID1(), Set.of(testResource1(), testResource2()));
    source.handleResources(primaryID1(), Set.of(testResource1()));

    verify(eventHandler, times(2)).handleEvent(new Event(primaryID1()));
    assertThat(source.getSecondaryResources(primaryID1())).containsExactly(testResource1());
  }

  @Test
  void addingAdditionalResource() {
    source.handleResources(primaryID1(), Set.of(testResource1()));
    source.handleResources(primaryID1(), Set.of(testResource1(), testResource2()));

    verify(eventHandler, times(2)).handleEvent(new Event(primaryID1()));
    assertThat(source.getSecondaryResources(primaryID1()))
        .containsExactlyInAnyOrder(testResource1(), testResource2());
  }

  @Test
  void replacingResource() {
    source.handleResources(primaryID1(), Set.of(testResource1()));
    source.handleResources(primaryID1(), Set.of(testResource2()));

    verify(eventHandler, times(2)).handleEvent(new Event(primaryID1()));
    assertThat(source.getSecondaryResources(primaryID1())).containsExactly(testResource2());
  }

  @Test
  void handlesDeleteFromMultipleResources() {
    source.handleResources(primaryID1(), Set.of(testResource1(), testResource2()));
    source.handleDelete(primaryID1(), testResource1());

    verify(eventHandler, times(2)).handleEvent(new Event(primaryID1()));
    assertThat(source.getSecondaryResources(primaryID1())).containsExactly(testResource2());
  }

  @Test
  void handlesDeleteAllFromMultipleResources() {
    source.handleResources(primaryID1(), Set.of(testResource1(), testResource2()));
    source.handleDeletes(primaryID1(), Set.of(testResource1(), testResource2()));

    verify(eventHandler, times(2)).handleEvent(new Event(primaryID1()));
    assertThat(source.getSecondaryResources(primaryID1())).isEmpty();
  }

  @Test
  void canFilterOnDeleteEvents() {
    TestExternalCachingEventSource delFilteringEventSource = new TestExternalCachingEventSource();
    delFilteringEventSource.setOnDeleteFilter((res, b) -> false);
    setUpSource(delFilteringEventSource);
    // try without any resources added
    source.handleDeletes(primaryID1(), Set.of(testResource1(), testResource2()));
    source.handleResources(primaryID1(), Set.of(testResource1(), testResource2()));
    // handling the add event
    verify(eventHandler, times(1)).handleEvent(any());

    source.handleDeletes(primaryID1(), Set.of(testResource1(), testResource2()));

    // no more invocation
    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  void filtersAddEvents() {
    TestExternalCachingEventSource delFilteringEventSource = new TestExternalCachingEventSource();
    delFilteringEventSource.setOnAddFilter((res) -> false);
    setUpSource(delFilteringEventSource);

    source.handleResources(primaryID1(), Set.of(testResource1()));
    verify(eventHandler, times(0)).handleEvent(any());

    source.handleResources(primaryID1(), Set.of(testResource1(), testResource2()));
    verify(eventHandler, times(0)).handleEvent(any());
  }

  @Test
  void filtersUpdateEvents() {
    TestExternalCachingEventSource delFilteringEventSource = new TestExternalCachingEventSource();
    delFilteringEventSource.setOnUpdateFilter((res, res2) -> false);
    setUpSource(delFilteringEventSource);
    source.handleResources(primaryID1(), Set.of(testResource1()));
    verify(eventHandler, times(1)).handleEvent(any());

    var resource = testResource1();
    resource.setValue("changed value");
    source.handleResources(primaryID1(), Set.of(resource));

    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  void filtersImplicitDeleteEvents() {
    TestExternalCachingEventSource delFilteringEventSource = new TestExternalCachingEventSource();
    delFilteringEventSource.setOnDeleteFilter((res, b) -> false);
    setUpSource(delFilteringEventSource);

    source.handleResources(primaryID1(), Set.of(testResource1(), testResource2()));
    verify(eventHandler, times(1)).handleEvent(any());

    source.handleResources(primaryID1(), Set.of(testResource1()));
    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  void genericFilteringEvents() {
    TestExternalCachingEventSource delFilteringEventSource = new TestExternalCachingEventSource();
    delFilteringEventSource.setGenericFilter(res -> false);
    setUpSource(delFilteringEventSource);

    source.handleResources(primaryID1(), Set.of(testResource1()));
    verify(eventHandler, times(0)).handleEvent(any());

    source.handleResources(primaryID1(), Set.of(testResource1(), testResource2()));
    verify(eventHandler, times(0)).handleEvent(any());

    source.handleResources(primaryID1(), Set.of(testResource2()));
    verify(eventHandler, times(0)).handleEvent(any());
  }

  public static class TestExternalCachingEventSource
      extends ExternalResourceCachingEventSource<SampleExternalResource, HasMetadata> {
    public TestExternalCachingEventSource() {
      super(SampleExternalResource.class, SampleExternalResource::getName);
    }
  }
}

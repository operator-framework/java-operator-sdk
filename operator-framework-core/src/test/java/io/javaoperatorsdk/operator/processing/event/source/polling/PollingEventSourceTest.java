package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSourceTestBase;
import io.javaoperatorsdk.operator.processing.event.source.SampleExternalResource;

import static io.javaoperatorsdk.operator.processing.event.source.SampleExternalResource.*;
import static org.mockito.Mockito.*;

class PollingEventSourceTest
    extends
    AbstractEventSourceTestBase<PollingEventSource<SampleExternalResource, HasMetadata>, EventHandler> {

  public static final int DEFAULT_WAIT_PERIOD = 100;

  private PollingEventSource.GenericResourceFetcher<SampleExternalResource> resourceFetcher =
      mock(PollingEventSource.GenericResourceFetcher.class);
  private final PollingEventSource<SampleExternalResource, HasMetadata> pollingEventSource =
      new PollingEventSource<>(resourceFetcher, 30L, SampleExternalResource.class,
          (SampleExternalResource er) -> er.getName() + "#" + er.getValue());

  @BeforeEach
  public void setup() {
    setUpSource(pollingEventSource, false);
  }

  @Test
  void pollsAndProcessesEvents() throws InterruptedException {
    when(resourceFetcher.fetchResources()).thenReturn(testResponseWithTwoValues());
    pollingEventSource.start();
    Thread.sleep(DEFAULT_WAIT_PERIOD);

    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  void propagatesEventForRemovedResources() throws InterruptedException {
    when(resourceFetcher.fetchResources()).thenReturn(testResponseWithTwoValues())
        .thenReturn(testResponseWithOneValue());
    pollingEventSource.start();
    Thread.sleep(DEFAULT_WAIT_PERIOD);

    verify(eventHandler, times(3)).handleEvent(any());
  }

  @Test
  void doesNotPropagateEventIfResourceNotChanged() throws InterruptedException {
    when(resourceFetcher.fetchResources()).thenReturn(testResponseWithTwoValues());
    pollingEventSource.start();
    Thread.sleep(DEFAULT_WAIT_PERIOD);

    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  void propagatesEventOnNewResourceForPrimary() throws InterruptedException {
    when(resourceFetcher.fetchResources())
        .thenReturn(testResponseWithOneValue())
        .thenReturn(testResponseWithTwoValueForSameId());

    pollingEventSource.start();
    Thread.sleep(DEFAULT_WAIT_PERIOD);

    verify(eventHandler, times(2)).handleEvent(any());
  }

  private Map<ResourceID, Set<SampleExternalResource>> testResponseWithTwoValueForSameId() {
    Map<ResourceID, Set<SampleExternalResource>> res = new HashMap<>();
    res.put(primaryID1(), Set.of(testResource1(), testResource2()));
    return res;
  }

  private Map<ResourceID, Set<SampleExternalResource>> testResponseWithOneValue() {
    Map<ResourceID, Set<SampleExternalResource>> res = new HashMap<>();
    res.put(primaryID1(), Set.of(testResource1()));
    return res;
  }

  private Map<ResourceID, Set<SampleExternalResource>> testResponseWithTwoValues() {
    Map<ResourceID, Set<SampleExternalResource>> res = new HashMap<>();
    res.put(primaryID1(), Set.of(testResource1()));
    res.put(primaryID2(), Set.of(testResource2()));
    return res;
  }

}

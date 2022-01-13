package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

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

  private Supplier<Map<ResourceID, SampleExternalResource>> supplier = mock(Supplier.class);

  @BeforeEach
  public void setup() {
    setUpSource(new PollingEventSource<>(supplier, 50, SampleExternalResource.class));
  }

  @Test
  public void pollsAndProcessesEvents() throws InterruptedException {
    when(supplier.get()).thenReturn(testResponseWithTwoValues());

    Thread.sleep(100);

    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  public void propagatesEventForRemovedResources() throws InterruptedException {
    when(supplier.get()).thenReturn(testResponseWithTwoValues())
        .thenReturn(testResponseWithOneValue());

    Thread.sleep(150);

    verify(eventHandler, times(3)).handleEvent(any());
  }

  @Test
  public void doesNotPropagateEventIfResourceNotChanged() throws InterruptedException {
    when(supplier.get()).thenReturn(testResponseWithTwoValues());

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

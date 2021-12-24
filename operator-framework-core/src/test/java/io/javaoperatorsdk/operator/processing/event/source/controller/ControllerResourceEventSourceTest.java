package io.javaoperatorsdk.operator.processing.event.source.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.config.DefaultControllerConfiguration;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSourceTestBase;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ControllerResourceEventSourceTest extends
    AbstractEventSourceTestBase<ControllerResourceEventSource<TestCustomResource>, EventHandler> {

  public static final String FINALIZER = "finalizer";

  private TestController testController = new TestController(true);

  @BeforeEach
  public void setup() {
    setUpSource(new ControllerResourceEventSource<>(testController), false);
  }

  @Test
  public void skipsEventHandlingIfGenerationNotIncreased() {
    TestCustomResource customResource = TestUtils.testCustomResource();
    customResource.getMetadata().setFinalizers(List.of(FINALIZER));
    customResource.getMetadata().setGeneration(2L);

    TestCustomResource oldCustomResource = TestUtils.testCustomResource();
    oldCustomResource.getMetadata().setFinalizers(List.of(FINALIZER));

    source.eventReceived(ResourceAction.UPDATED, customResource, oldCustomResource);
    verify(eventHandler, times(1)).handleEvent(any());

    source.eventReceived(ResourceAction.UPDATED, customResource, customResource);
    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  public void dontSkipEventHandlingIfMarkedForDeletion() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    source.eventReceived(ResourceAction.UPDATED, customResource1, customResource1);
    verify(eventHandler, times(1)).handleEvent(any());

    // mark for deletion
    customResource1.getMetadata().setDeletionTimestamp(LocalDateTime.now().toString());
    source.eventReceived(ResourceAction.UPDATED, customResource1, customResource1);
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  public void normalExecutionIfGenerationChanges() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    source.eventReceived(ResourceAction.UPDATED, customResource1, customResource1);
    verify(eventHandler, times(1)).handleEvent(any());

    customResource1.getMetadata().setGeneration(2L);
    source.eventReceived(ResourceAction.UPDATED, customResource1, customResource1);
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  public void handlesAllEventIfNotGenerationAware() {
    source =
        new ControllerResourceEventSource<>(new TestController(false));
    setup();

    TestCustomResource customResource1 = TestUtils.testCustomResource();

    source.eventReceived(ResourceAction.UPDATED, customResource1, customResource1);
    verify(eventHandler, times(1)).handleEvent(any());

    source.eventReceived(ResourceAction.UPDATED, customResource1, customResource1);
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  public void eventWithNoGenerationProcessedIfNoFinalizer() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    source.eventReceived(ResourceAction.UPDATED, customResource1, customResource1);

    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  public void handlesNextEventIfWhitelisted() {
    TestCustomResource customResource = TestUtils.testCustomResource();
    customResource.getMetadata().setFinalizers(List.of(FINALIZER));
    source.whitelistNextEvent(ResourceID.fromResource(customResource));

    source.eventReceived(ResourceAction.UPDATED, customResource, customResource);

    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  public void notHandlesNextEventIfNotWhitelisted() {
    TestCustomResource customResource = TestUtils.testCustomResource();
    customResource.getMetadata().setFinalizers(List.of(FINALIZER));

    source.eventReceived(ResourceAction.UPDATED, customResource, customResource);

    verify(eventHandler, times(0)).handleEvent(any());
  }

  @Test
  public void callsBroadcastsOnResourceEvents() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    source.eventReceived(ResourceAction.UPDATED, customResource1, customResource1);

    verify(testController.getEventSourceManager(), times(1))
        .broadcastOnResourceEvent(eq(ResourceAction.UPDATED), eq(customResource1),
            eq(customResource1));
  }

  private static class TestController extends Controller<TestCustomResource> {

    private EventSourceManager<TestCustomResource> eventSourceManager =
        mock(EventSourceManager.class);

    public TestController(boolean generationAware) {
      super(null, new TestConfiguration(generationAware),
          MockKubernetesClient.client(TestCustomResource.class));
    }

    @Override
    public EventSourceManager<TestCustomResource> getEventSourceManager() {
      return eventSourceManager;
    }
  }

  private static class TestConfiguration extends
      DefaultControllerConfiguration<TestCustomResource> {

    public TestConfiguration(boolean generationAware) {
      super(
          null,
          null,
          null,
          FINALIZER,
          generationAware,
          null,
          null,
          null,
          null,
          TestCustomResource.class,
          null,
          null);
    }
  }
}

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
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSourceTestBase;
import io.javaoperatorsdk.operator.processing.event.source.filter.EventFilter;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ControllerResourceEventSourceTest extends
    AbstractEventSourceTestBase<ControllerResourceEventSource<TestCustomResource>, EventHandler> {

  public static final String FINALIZER = "finalizer";

  private final TestController testController = new TestController(true);

  @BeforeEach
  public void setup() {
    setUpSource(new ControllerResourceEventSource<>(testController), false);
  }

  @Test
  void skipsEventHandlingIfGenerationNotIncreased() {
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
  void dontSkipEventHandlingIfMarkedForDeletion() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    source.eventReceived(ResourceAction.UPDATED, customResource1, customResource1);
    verify(eventHandler, times(1)).handleEvent(any());

    // mark for deletion
    customResource1.getMetadata().setDeletionTimestamp(LocalDateTime.now().toString());
    source.eventReceived(ResourceAction.UPDATED, customResource1, customResource1);
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  void normalExecutionIfGenerationChanges() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    source.eventReceived(ResourceAction.UPDATED, customResource1, customResource1);
    verify(eventHandler, times(1)).handleEvent(any());

    customResource1.getMetadata().setGeneration(2L);
    source.eventReceived(ResourceAction.UPDATED, customResource1, customResource1);
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  void handlesAllEventIfNotGenerationAware() {
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
  void eventWithNoGenerationProcessedIfNoFinalizer() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    source.eventReceived(ResourceAction.UPDATED, customResource1, customResource1);

    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  void callsBroadcastsOnResourceEvents() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    source.eventReceived(ResourceAction.UPDATED, customResource1, customResource1);

    verify(testController.getEventSourceManager(), times(1))
        .broadcastOnResourceEvent(eq(ResourceAction.UPDATED), eq(customResource1),
            eq(customResource1));
  }

  @Test
  void filtersOutEventsOnAddAndUpdate() {
    TestCustomResource cr = TestUtils.testCustomResource();

    source =
        new ControllerResourceEventSource<>(
            new TestController(new EventFilter<>() {
              @Override
              public boolean acceptsAdding(TestCustomResource resource) {
                return false;
              }

              @Override
              public boolean acceptsUpdating(TestCustomResource from, TestCustomResource to) {
                return false;
              }
            }));
    setUpSource(source);

    source.eventReceived(ResourceAction.ADDED, cr, null);
    source.eventReceived(ResourceAction.UPDATED, cr, cr);

    verify(eventHandler, never()).handleEvent(any());
  }

  @Test
  void genericFilterFiltersOutAddUpdateAndDeleteEvents() {
    TestCustomResource cr = TestUtils.testCustomResource();

    source =
        new ControllerResourceEventSource<>(new TestController(
            new EventFilter<>() {
              @Override
              public boolean rejects(TestCustomResource resource) {
                return true;
              }
            }));
    setUpSource(source);

    source.eventReceived(ResourceAction.ADDED, cr, null);
    source.eventReceived(ResourceAction.UPDATED, cr, cr);
    source.eventReceived(ResourceAction.DELETED, cr, cr);

    verify(eventHandler, never()).handleEvent(any());
  }

  @SuppressWarnings("unchecked")
  private static class TestController extends Controller<TestCustomResource> {

    private final EventSourceManager<TestCustomResource> eventSourceManager =
        mock(EventSourceManager.class);

    public TestController(EventFilter<TestCustomResource> filter) {
      super(null, new TestConfiguration(true, filter),
          MockKubernetesClient.client(TestCustomResource.class));
    }

    public TestController(boolean generationAware) {
      super(null, new TestConfiguration(generationAware, null),
          MockKubernetesClient.client(TestCustomResource.class));
    }

    @Override
    public EventSourceManager<TestCustomResource> getEventSourceManager() {
      return eventSourceManager;
    }

    @Override
    public boolean useFinalizer() {
      return true;
    }
  }

  private static class TestConfiguration extends
      DefaultControllerConfiguration<TestCustomResource> {

    public TestConfiguration(boolean generationAware, EventFilter<TestCustomResource> filter) {
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
          filter, null, null);
    }
  }
}

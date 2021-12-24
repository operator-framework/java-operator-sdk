package io.javaoperatorsdk.operator.processing.event.source;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.DefaultControllerConfiguration;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.sample.observedgeneration.ObservedGenCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ResourceEventFilterTest {
  public static final String FINALIZER = "finalizer";

  private EventHandler eventHandler;

  @BeforeEach
  public void before() {
    this.eventHandler = mock(EventHandler.class);
  }

  private <T extends HasMetadata> ControllerResourceEventSource<T> init(Controller<T> controller) {
    var eventSource = new ControllerResourceEventSource<>(controller);
    eventSource.setEventHandler(eventHandler);
    return eventSource;
  }

  @Test
  public void eventFilteredByCustomPredicate() {
    var config = new TestControllerConfig(
        FINALIZER,
        false,
        (configuration, oldResource, newResource) -> oldResource == null || !Objects.equals(
            oldResource.getStatus().getConfigMapStatus(),
            newResource.getStatus().getConfigMapStatus()));

    final var eventSource = init(new TestController(config));

    TestCustomResource cr = TestUtils.testCustomResource();
    cr.getMetadata().setFinalizers(List.of(FINALIZER));
    cr.getMetadata().setGeneration(1L);
    cr.getStatus().setConfigMapStatus("1");

    eventSource.eventReceived(ResourceAction.UPDATED, cr, null);
    verify(eventHandler, times(1)).handleEvent(any());

    cr.getMetadata().setGeneration(1L);
    cr.getStatus().setConfigMapStatus("1");

    eventSource.eventReceived(ResourceAction.UPDATED, cr, cr);
    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  public void eventFilteredByCustomPredicateAndGenerationAware() {
    var config = new TestControllerConfig(
        FINALIZER,
        true,
        (configuration, oldResource, newResource) -> oldResource == null || !Objects.equals(
            oldResource.getStatus().getConfigMapStatus(),
            newResource.getStatus().getConfigMapStatus()));

    final var eventSource = init(new TestController(config));

    TestCustomResource cr = TestUtils.testCustomResource();
    cr.getMetadata().setFinalizers(List.of(FINALIZER));
    cr.getMetadata().setGeneration(1L);
    cr.getStatus().setConfigMapStatus("1");

    TestCustomResource cr2 = TestUtils.testCustomResource();
    cr.getMetadata().setFinalizers(List.of(FINALIZER));
    cr.getMetadata().setGeneration(2L);
    cr.getStatus().setConfigMapStatus("1");

    eventSource.eventReceived(ResourceAction.UPDATED, cr, cr2);
    verify(eventHandler, times(1)).handleEvent(any());

    cr.getMetadata().setGeneration(1L);
    cr.getStatus().setConfigMapStatus("2");

    eventSource.eventReceived(ResourceAction.UPDATED, cr, cr);
    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  public void observedGenerationFiltering() {
    var config = new ObservedGenControllerConfig(FINALIZER, true, null);

    var eventSource = init(new ObservedGenController(config));

    ObservedGenCustomResource cr = new ObservedGenCustomResource();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setFinalizers(List.of(FINALIZER));
    cr.getMetadata().setGeneration(5L);
    cr.getStatus().setObservedGeneration(5L);

    eventSource.eventReceived(ResourceAction.UPDATED, cr, null);
    verify(eventHandler, times(0)).handleEvent(any());

    cr.getMetadata().setGeneration(6L);

    eventSource.eventReceived(ResourceAction.UPDATED, cr, null);
    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  public void eventAlwaysFilteredByCustomPredicate() {
    var config = new TestControllerConfig(
        FINALIZER,
        false,
        (configuration, oldResource, newResource) -> !Objects.equals(
            oldResource.getStatus().getConfigMapStatus(),
            newResource.getStatus().getConfigMapStatus()));

    final var eventSource = init(new TestController(config));

    TestCustomResource cr = TestUtils.testCustomResource();
    cr.getMetadata().setGeneration(1L);
    cr.getStatus().setConfigMapStatus("1");

    eventSource.eventReceived(ResourceAction.UPDATED, cr, cr);
    verify(eventHandler, times(0)).handleEvent(any());
  }

  private static class TestControllerConfig extends ControllerConfig<TestCustomResource> {
    public TestControllerConfig(String finalizer, boolean generationAware,
        ResourceEventFilter<TestCustomResource> eventFilter) {
      super(finalizer, generationAware, eventFilter, TestCustomResource.class);
    }
  }
  private static class ObservedGenControllerConfig
      extends ControllerConfig<ObservedGenCustomResource> {
    public ObservedGenControllerConfig(String finalizer, boolean generationAware,
        ResourceEventFilter<ObservedGenCustomResource> eventFilter) {
      super(finalizer, generationAware, eventFilter, ObservedGenCustomResource.class);
    }
  }

  private static class ControllerConfig<T extends HasMetadata> extends
      DefaultControllerConfiguration<T> {

    public ControllerConfig(String finalizer, boolean generationAware,
        ResourceEventFilter<T> eventFilter, Class<T> customResourceClass) {
      super(
          null,
          null,
          null,
          finalizer,
          generationAware,
          null,
          null,
          null,
          eventFilter,
          customResourceClass,
          null,
          null);
    }
  }

  private static class TestController extends Controller<TestCustomResource> {

    public TestController(ControllerConfiguration<TestCustomResource> configuration) {
      super(null, configuration, MockKubernetesClient.client(TestCustomResource.class));
    }

    @Override
    public EventSourceManager<TestCustomResource> getEventSourceManager() {
      return mock(EventSourceManager.class);
    }
  }

  private static class ObservedGenController
      extends Controller<ObservedGenCustomResource> {

    public ObservedGenController(
        ControllerConfiguration<ObservedGenCustomResource> configuration) {
      super(null, configuration, MockKubernetesClient.client(ObservedGenCustomResource.class));
    }

    @Override
    public EventSourceManager<ObservedGenCustomResource> getEventSourceManager() {
      return mock(EventSourceManager.class);
    }
  }
}

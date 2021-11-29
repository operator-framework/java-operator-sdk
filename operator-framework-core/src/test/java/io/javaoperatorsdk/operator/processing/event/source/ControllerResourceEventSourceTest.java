package io.javaoperatorsdk.operator.processing.event.source;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.DefaultControllerConfiguration;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceAction;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ControllerResourceEventSourceTest {

  public static final String FINALIZER = "finalizer";
  private static final MixedOperation<TestCustomResource, KubernetesResourceList<TestCustomResource>, Resource<TestCustomResource>> client =
      mock(MixedOperation.class);
  EventHandler eventHandler = mock(EventHandler.class);

  private ControllerResourceEventSource<TestCustomResource> controllerResourceEventSource =
      new ControllerResourceEventSource<>(new TestController(true));

  @BeforeEach
  public void setup() {
    controllerResourceEventSource.setEventHandler(eventHandler);
  }

  @Test
  public void skipsEventHandlingIfGenerationNotIncreased() {
    TestCustomResource customResource = TestUtils.testCustomResource();
    customResource.getMetadata().setFinalizers(List.of(FINALIZER));
    customResource.getMetadata().setGeneration(2L);

    TestCustomResource oldCustomResource = TestUtils.testCustomResource();
    oldCustomResource.getMetadata().setFinalizers(List.of(FINALIZER));

    controllerResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource,
        oldCustomResource);
    verify(eventHandler, times(1)).handleEvent(any());

    controllerResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource,
        customResource);
    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  public void dontSkipEventHandlingIfMarkedForDeletion() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    controllerResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource1,
        customResource1);
    verify(eventHandler, times(1)).handleEvent(any());

    // mark for deletion
    customResource1.getMetadata().setDeletionTimestamp(LocalDateTime.now().toString());
    controllerResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource1,
        customResource1);
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  public void normalExecutionIfGenerationChanges() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    controllerResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource1,
        customResource1);
    verify(eventHandler, times(1)).handleEvent(any());

    customResource1.getMetadata().setGeneration(2L);
    controllerResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource1,
        customResource1);
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  public void handlesAllEventIfNotGenerationAware() {
    controllerResourceEventSource =
        new ControllerResourceEventSource<>(new TestController(false));
    setup();

    TestCustomResource customResource1 = TestUtils.testCustomResource();

    controllerResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource1,
        customResource1);
    verify(eventHandler, times(1)).handleEvent(any());

    controllerResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource1,
        customResource1);
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  public void eventWithNoGenerationProcessedIfNoFinalizer() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    controllerResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource1,
        customResource1);

    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  public void handlesNextEventIfWhitelisted() {
    TestCustomResource customResource = TestUtils.testCustomResource();
    customResource.getMetadata().setFinalizers(List.of(FINALIZER));
    controllerResourceEventSource.whitelistNextEvent(ResourceID.fromResource(customResource));

    controllerResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource,
        customResource);

    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  public void notHandlesNextEventIfNotWhitelisted() {
    TestCustomResource customResource = TestUtils.testCustomResource();
    customResource.getMetadata().setFinalizers(List.of(FINALIZER));

    controllerResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource,
        customResource);

    verify(eventHandler, times(0)).handleEvent(any());
  }

  private static class TestController extends Controller<TestCustomResource> {

    public TestController(boolean generationAware) {
      super(null, new TestConfiguration(generationAware), null);
    }

    @Override
    public MixedOperation<TestCustomResource, KubernetesResourceList<TestCustomResource>, Resource<TestCustomResource>> getCRClient() {
      return client;
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
          mock(ConfigurationService.class));
      when(getConfigurationService().getResourceCloner())
          .thenReturn(ConfigurationService.DEFAULT_CLONER);
      when(getConfigurationService().getMetrics())
          .thenReturn(Metrics.NOOP);
    }
  }
}

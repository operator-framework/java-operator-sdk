package io.javaoperatorsdk.operator.processing.event.internal;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.Metrics;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.DefaultControllerConfiguration;
import io.javaoperatorsdk.operator.processing.ConfiguredController;
import io.javaoperatorsdk.operator.processing.event.CustomResourceID;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomResourceEventSourceTest {

  public static final String FINALIZER = "finalizer";
  private static final MixedOperation<TestCustomResource, KubernetesResourceList<TestCustomResource>, Resource<TestCustomResource>> client =
      mock(MixedOperation.class);
  EventHandler eventHandler = mock(EventHandler.class);

  private CustomResourceEventSource<TestCustomResource> customResourceEventSource =
      new CustomResourceEventSource<>(new TestConfiguredController(true));

  @BeforeEach
  public void setup() {
    customResourceEventSource.setEventHandler(eventHandler);
  }

  @Test
  public void skipsEventHandlingIfGenerationNotIncreased() {
    TestCustomResource customResource = TestUtils.testCustomResource();
    customResource.getMetadata().setFinalizers(List.of(FINALIZER));
    customResource.getMetadata().setGeneration(2L);

    TestCustomResource oldCustomResource = TestUtils.testCustomResource();
    oldCustomResource.getMetadata().setFinalizers(List.of(FINALIZER));

    customResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource,
        oldCustomResource);
    verify(eventHandler, times(1)).handleEvent(any());

    customResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource,
        customResource);
    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  public void dontSkipEventHandlingIfMarkedForDeletion() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    customResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource1,
        customResource1);
    verify(eventHandler, times(1)).handleEvent(any());

    // mark for deletion
    customResource1.getMetadata().setDeletionTimestamp(LocalDateTime.now().toString());
    customResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource1,
        customResource1);
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  public void normalExecutionIfGenerationChanges() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    customResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource1,
        customResource1);
    verify(eventHandler, times(1)).handleEvent(any());

    customResource1.getMetadata().setGeneration(2L);
    customResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource1,
        customResource1);
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  public void handlesAllEventIfNotGenerationAware() {
    customResourceEventSource =
        new CustomResourceEventSource<>(new TestConfiguredController(false));
    setup();

    TestCustomResource customResource1 = TestUtils.testCustomResource();

    customResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource1,
        customResource1);
    verify(eventHandler, times(1)).handleEvent(any());

    customResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource1,
        customResource1);
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  public void eventWithNoGenerationProcessedIfNoFinalizer() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    customResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource1,
        customResource1);

    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  public void handlesNextEventIfWhitelisted() {
    TestCustomResource customResource = TestUtils.testCustomResource();
    customResource.getMetadata().setFinalizers(List.of(FINALIZER));
    customResourceEventSource.whitelistNextEvent(CustomResourceID.fromResource(customResource));

    customResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource,
        customResource);

    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  public void notHandlesNextEventIfNotWhitelisted() {
    TestCustomResource customResource = TestUtils.testCustomResource();
    customResource.getMetadata().setFinalizers(List.of(FINALIZER));

    customResourceEventSource.eventReceived(ResourceAction.UPDATED, customResource,
        customResource);

    verify(eventHandler, times(0)).handleEvent(any());
  }

  private static class TestConfiguredController extends ConfiguredController<TestCustomResource> {

    public TestConfiguredController(boolean generationAware) {
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

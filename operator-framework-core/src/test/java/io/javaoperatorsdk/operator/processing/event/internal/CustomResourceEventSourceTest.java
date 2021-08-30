package io.javaoperatorsdk.operator.processing.event.internal;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import java.time.LocalDateTime;
import java.util.Arrays;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.processing.ConfiguredController;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

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
    TestCustomResource customResource1 = TestUtils.testCustomResource();
    customResource1.getMetadata().setFinalizers(List.of(FINALIZER));

    customResourceEventSource.eventReceived(Watcher.Action.MODIFIED, customResource1);
    verify(eventHandler, times(1)).handleEvent(any());

    customResourceEventSource.eventReceived(Watcher.Action.MODIFIED, customResource1);
    verify(eventHandler, times(1)).handleEvent(any());
  }

  @Test
  public void dontSkipEventHandlingIfMarkedForDeletion() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    customResourceEventSource.eventReceived(Watcher.Action.MODIFIED, customResource1);
    verify(eventHandler, times(1)).handleEvent(any());

    // mark for deletion
    customResource1.getMetadata().setDeletionTimestamp(LocalDateTime.now().toString());
    customResourceEventSource.eventReceived(Watcher.Action.MODIFIED, customResource1);
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  public void normalExecutionIfGenerationChanges() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    customResourceEventSource.eventReceived(Watcher.Action.MODIFIED, customResource1);
    verify(eventHandler, times(1)).handleEvent(any());

    customResource1.getMetadata().setGeneration(2L);
    customResourceEventSource.eventReceived(Watcher.Action.MODIFIED, customResource1);
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  public void handlesAllEventIfNotGenerationAware() {
    customResourceEventSource =
        new CustomResourceEventSource<>(new TestConfiguredController(false));
    setup();

    TestCustomResource customResource1 = TestUtils.testCustomResource();

    customResourceEventSource.eventReceived(Watcher.Action.MODIFIED, customResource1);
    verify(eventHandler, times(1)).handleEvent(any());

    customResourceEventSource.eventReceived(Watcher.Action.MODIFIED, customResource1);
    verify(eventHandler, times(2)).handleEvent(any());
  }

  @Test
  public void eventNotMarkedForLastGenerationIfNoFinalizer() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();

    customResourceEventSource.eventReceived(Watcher.Action.MODIFIED, customResource1);
    verify(eventHandler, times(1)).handleEvent(any());

    customResourceEventSource.eventReceived(Watcher.Action.MODIFIED, customResource1);
    verify(eventHandler, times(2)).handleEvent(any());
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
  private static class TestConfiguration implements
      ControllerConfiguration<TestCustomResource> {

    final ConfigurationService service = mock(ConfigurationService.class);
    final boolean generationAware;

    public TestConfiguration(boolean generationAware) {
      when(service.getObjectMapper()).thenReturn(ConfigurationService.OBJECT_MAPPER);
      this.generationAware = generationAware;
    }

    @Override
    public String getAssociatedControllerClassName() {
      return null;
    }

    @Override
    public ConfigurationService getConfigurationService() {
      return service;
    }

    @Override
    public boolean isGenerationAware() {
      return generationAware;
    }
  }
}

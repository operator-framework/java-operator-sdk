package io.javaoperatorsdk.operator.processing.event.internal;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.Metrics;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.DefaultControllerConfiguration;
import io.javaoperatorsdk.operator.processing.ConfiguredController;
import io.javaoperatorsdk.operator.processing.CustomResourceCache;
import io.javaoperatorsdk.operator.processing.DefaultEventHandler;
import io.javaoperatorsdk.operator.processing.EventDispatcher;
import io.javaoperatorsdk.operator.processing.event.DefaultEventSourceManager;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
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

  @Test
  public void restartingShouldResumeEventHandling() throws IOException {
    final var cr = TestUtils.testCustomResource();

    CustomResourceCache customResourceCache = new CustomResourceCache();
    customResourceCache.cacheResource(cr);
    DefaultEventSourceManager defaultEventSourceManagerMock =
        mock(DefaultEventSourceManager.class);
    EventDispatcher eventDispatcherMock = mock(EventDispatcher.class);
    DefaultEventHandler local = new DefaultEventHandler(eventDispatcherMock, "Test",
        null);
    local.setEventSourceManager(defaultEventSourceManagerMock);
    when(defaultEventSourceManagerMock.getCache()).thenReturn(customResourceCache);
    doCallRealMethod().when(defaultEventSourceManagerMock).getLatestResource(any());
    doCallRealMethod().when(defaultEventSourceManagerMock).getLatestResources(any());
    doCallRealMethod().when(defaultEventSourceManagerMock).getLatestResourceUids(any());
    doCallRealMethod().when(defaultEventSourceManagerMock).cacheResource(any(), any());

    customResourceEventSource.setEventHandler(local);

    customResourceEventSource.eventReceived(Watcher.Action.MODIFIED, cr);
    verify(eventDispatcherMock, timeout(50).times(1)).handleExecution(any());
    waitMinimalTime();


    customResourceEventSource.close();
    assertFalse(local.isRunning());
    waitMinimalTime();
    customResourceEventSource.eventReceived(Watcher.Action.MODIFIED, cr);
    verify(eventDispatcherMock, timeout(50).times(1)).handleExecution(any());

    customResourceEventSource.start();
    assertTrue(local.isRunning());
    waitMinimalTime();
    customResourceEventSource.eventReceived(Watcher.Action.MODIFIED, cr);
    verify(eventDispatcherMock, timeout(50).times(1)).handleExecution(any());
  }

  private void waitMinimalTime() {
    try {
      Thread.sleep(200);
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
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
      when(getConfigurationService().getObjectMapper())
          .thenReturn(ConfigurationService.OBJECT_MAPPER);
      when(getConfigurationService().getMetrics())
          .thenReturn(Metrics.NOOP);
    }
  }
}

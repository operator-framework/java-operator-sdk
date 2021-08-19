package io.javaoperatorsdk.operator.processing.event.internal;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomResourceEventSourceTest {

  public static final String FINALIZER = "finalizer";
  MixedOperation<TestCustomResource, KubernetesResourceList<TestCustomResource>, Resource<TestCustomResource>> client =
      mock(MixedOperation.class);
  EventHandler eventHandler = mock(EventHandler.class);

  private CustomResourceEventSource<TestCustomResource> customResourceEventSource =
      new CustomResourceEventSource<>(
          client, null, true, FINALIZER, null, TestCustomResource.class);

  @BeforeEach
  public void setup() {
    customResourceEventSource.setEventHandler(eventHandler);
  }

  @Test
  public void skipsEventHandlingIfGenerationNotIncreased() {
    TestCustomResource customResource1 = TestUtils.testCustomResource();
    customResource1.getMetadata().setFinalizers(Arrays.asList(FINALIZER));

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
        new CustomResourceEventSource<>(
            client, null, false, FINALIZER, null, TestCustomResource.class);
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
}

package io.javaoperatorsdk.operator.processing.event.internal;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.DefaultControllerConfiguration;
import io.javaoperatorsdk.operator.processing.ConfiguredController;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomResourceEventFilterTest {
  public static final String FINALIZER = "finalizer";

  private EventHandler eventHandler;

  @BeforeEach
  public void before() {
    this.eventHandler = mock(EventHandler.class);
  }

  @Test
  public void eventFilteredByCustomPredicate() {
    var config = new TestControllerConfig(
        FINALIZER,
        false,
        (configuration, oldResource, newResource) -> oldResource == null || !Objects.equals(
            oldResource.getStatus().getConfigMapStatus(),
            newResource.getStatus().getConfigMapStatus()));

    var controller = new TestConfiguredController(config);
    var eventSource = new CustomResourceEventSource<>(controller);
    eventSource.setEventHandler(eventHandler);

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

    var controller = new TestConfiguredController(config);
    var eventSource = new CustomResourceEventSource<>(controller);
    eventSource.setEventHandler(eventHandler);

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
  public void eventNotFilteredByCustomPredicateIfFinalizerIsRequired() {
    var config = new TestControllerConfig(
        FINALIZER,
        false,
        (configuration, oldResource, newResource) -> !Objects.equals(
            oldResource.getStatus().getConfigMapStatus(),
            newResource.getStatus().getConfigMapStatus()));

    when(config.getConfigurationService().getResourceCloner())
        .thenReturn(ConfigurationService.DEFAULT_CLONER);

    var controller = new TestConfiguredController(config);
    var eventSource = new CustomResourceEventSource<>(controller);
    eventSource.setEventHandler(eventHandler);

    TestCustomResource cr = TestUtils.testCustomResource();
    cr.getMetadata().setGeneration(1L);
    cr.getStatus().setConfigMapStatus("1");

    eventSource.eventReceived(ResourceAction.UPDATED, cr, cr);
    verify(eventHandler, times(1)).handleEvent(any());

    cr.getMetadata().setGeneration(1L);
    cr.getStatus().setConfigMapStatus("1");

    eventSource.eventReceived(ResourceAction.UPDATED, cr, cr);
    verify(eventHandler, times(2)).handleEvent(any());
  }

  private static class TestControllerConfig extends
      DefaultControllerConfiguration<TestCustomResource> {

    public TestControllerConfig(String finalizer, boolean generationAware,
        CustomResourceEventFilter<TestCustomResource> eventFilter) {
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
          TestCustomResource.class,
          mock(ConfigurationService.class));

      when(getConfigurationService().getResourceCloner())
          .thenReturn(ConfigurationService.DEFAULT_CLONER);
    }
  }

  private static class TestConfiguredController extends ConfiguredController<TestCustomResource> {

    public TestConfiguredController(ControllerConfiguration<TestCustomResource> configuration) {
      super(null, configuration, null);
    }

    @Override
    public MixedOperation<TestCustomResource, KubernetesResourceList<TestCustomResource>, Resource<TestCustomResource>> getCRClient() {
      return mock(MixedOperation.class);
    }
  }
}

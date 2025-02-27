package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.InformerStoppedHandler;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class InformerEventSourceTest {

  private static final String PREV_RESOURCE_VERSION = "0";
  private static final String DEFAULT_RESOURCE_VERSION = "1";

  private InformerEventSource<Deployment, TestCustomResource> informerEventSource;
  private final KubernetesClient clientMock = MockKubernetesClient.client(Deployment.class);
  private final TemporaryResourceCache<Deployment> temporaryResourceCacheMock =
      mock(TemporaryResourceCache.class);
  private final EventHandler eventHandlerMock = mock(EventHandler.class);
  private final InformerEventSourceConfiguration<Deployment> informerEventSourceConfiguration =
      mock(InformerEventSourceConfiguration.class);

  @BeforeEach
  void setup() {
    final var informerConfig = mock(InformerConfiguration.class);
    when(informerEventSourceConfiguration.getInformerConfig()).thenReturn(informerConfig);
    when(informerConfig.getEffectiveNamespaces(any())).thenReturn(DEFAULT_NAMESPACES_SET);
    when(informerEventSourceConfiguration.getSecondaryToPrimaryMapper())
        .thenReturn(mock(SecondaryToPrimaryMapper.class));
    when(informerEventSourceConfiguration.getResourceClass()).thenReturn(Deployment.class);

    informerEventSource = new InformerEventSource<>(informerEventSourceConfiguration, clientMock);

    var mockControllerConfig = mock(ControllerConfiguration.class);
    when(mockControllerConfig.getConfigurationService()).thenReturn(new BaseConfigurationService());

    informerEventSource.setEventHandler(eventHandlerMock);
    informerEventSource.setControllerConfiguration(mockControllerConfig);
    SecondaryToPrimaryMapper secondaryToPrimaryMapper = mock(SecondaryToPrimaryMapper.class);
    when(informerEventSourceConfiguration.getSecondaryToPrimaryMapper())
        .thenReturn(secondaryToPrimaryMapper);
    when(secondaryToPrimaryMapper.toPrimaryResourceIDs(any()))
        .thenReturn(Set.of(ResourceID.fromResource(testDeployment())));
    informerEventSource.start();
    informerEventSource.setTemporalResourceCache(temporaryResourceCacheMock);
  }

  @Test
  void skipsEventPropagationIfResourceWithSameVersionInResourceCache() {
    when(temporaryResourceCacheMock.getResourceFromCache(any()))
        .thenReturn(Optional.of(testDeployment()));

    informerEventSource.onAdd(testDeployment());
    informerEventSource.onUpdate(testDeployment(), testDeployment());

    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void skipsAddEventPropagationViaAnnotation() {
    informerEventSource.onAdd(informerEventSource.addPreviousAnnotation(null, testDeployment()));

    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void skipsUpdateEventPropagationViaAnnotation() {
    informerEventSource.onUpdate(
        testDeployment(), informerEventSource.addPreviousAnnotation("1", testDeployment()));

    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void processEventPropagationWithoutAnnotation() {
    informerEventSource.onUpdate(testDeployment(), testDeployment());

    verify(eventHandlerMock, times(1)).handleEvent(any());
  }

  @Test
  void processEventPropagationWithIncorrectAnnotation() {
    informerEventSource.onAdd(
        new DeploymentBuilder(testDeployment())
            .editMetadata()
            .addToAnnotations(InformerEventSource.PREVIOUS_ANNOTATION_KEY, "invalid")
            .endMetadata()
            .build());

    verify(eventHandlerMock, times(1)).handleEvent(any());
  }

  @Test
  void propagateEventAndRemoveResourceFromTempCacheIfResourceVersionMismatch() {
    Deployment cachedDeployment = testDeployment();
    cachedDeployment.getMetadata().setResourceVersion(PREV_RESOURCE_VERSION);
    when(temporaryResourceCacheMock.getResourceFromCache(any()))
        .thenReturn(Optional.of(cachedDeployment));

    informerEventSource.onUpdate(cachedDeployment, testDeployment());

    verify(eventHandlerMock, times(1)).handleEvent(any());
    verify(temporaryResourceCacheMock, times(1)).onAddOrUpdateEvent(testDeployment());
  }

  @Test
  void genericFilterForEvents() {
    informerEventSource.setGenericFilter(r -> false);
    when(temporaryResourceCacheMock.getResourceFromCache(any())).thenReturn(Optional.empty());

    informerEventSource.onAdd(testDeployment());
    informerEventSource.onUpdate(testDeployment(), testDeployment());
    informerEventSource.onDelete(testDeployment(), true);

    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void filtersOnAddEvents() {
    informerEventSource.setOnAddFilter(r -> false);
    when(temporaryResourceCacheMock.getResourceFromCache(any())).thenReturn(Optional.empty());

    informerEventSource.onAdd(testDeployment());

    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void filtersOnUpdateEvents() {
    informerEventSource.setOnUpdateFilter((r1, r2) -> false);
    when(temporaryResourceCacheMock.getResourceFromCache(any())).thenReturn(Optional.empty());

    informerEventSource.onUpdate(testDeployment(), testDeployment());

    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void filtersOnDeleteEvents() {
    informerEventSource.setOnDeleteFilter((r, b) -> false);
    when(temporaryResourceCacheMock.getResourceFromCache(any())).thenReturn(Optional.empty());

    informerEventSource.onDelete(testDeployment(), true);

    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void informerStoppedHandlerShouldBeCalledWhenInformerStops() {
    final var exception = new RuntimeException("Informer stopped exceptionally!");
    final var informerStoppedHandler = mock(InformerStoppedHandler.class);
    var configuration =
        ConfigurationService.newOverriddenConfigurationService(
            new BaseConfigurationService(),
            o -> o.withInformerStoppedHandler(informerStoppedHandler));

    var mockControllerConfig = mock(ControllerConfiguration.class);
    when(mockControllerConfig.getConfigurationService()).thenReturn(configuration);

    informerEventSource =
        new InformerEventSource<>(
            informerEventSourceConfiguration,
            MockKubernetesClient.client(
                Deployment.class,
                unused -> {
                  throw exception;
                }));
    informerEventSource.setControllerConfiguration(mockControllerConfig);

    // by default informer fails to start if there is an exception in the client on start.
    // Throws the exception further.
    assertThrows(OperatorException.class, () -> informerEventSource.start());
    verify(informerStoppedHandler, atLeastOnce()).onStop(any(), eq(exception));
  }

  Deployment testDeployment() {
    Deployment deployment = new Deployment();
    deployment.setMetadata(new ObjectMeta());
    deployment.getMetadata().setResourceVersion(DEFAULT_RESOURCE_VERSION);
    deployment.getMetadata().setName("test");
    return deployment;
  }
}

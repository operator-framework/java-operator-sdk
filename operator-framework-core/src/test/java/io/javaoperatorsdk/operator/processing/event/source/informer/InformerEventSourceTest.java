package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.AnyNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Indexer;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class InformerEventSourceTest {

  private static final String PREV_RESOURCE_VERSION = "0";
  private static final String DEFAULT_RESOURCE_VERSION = "1";
  private static final String NEXT_RESOURCE_VERSION = "2";

  private InformerEventSource<Deployment, TestCustomResource> informerEventSource;
  private final KubernetesClient clientMock = mock(KubernetesClient.class);
  private final TemporaryResourceCache<Deployment> temporaryResourceCacheMock =
      mock(TemporaryResourceCache.class);
  private final EventHandler eventHandlerMock = mock(EventHandler.class);
  private final MixedOperation crClientMock = mock(MixedOperation.class);
  private final AnyNamespaceOperation specificResourceClientMock =
      mock(AnyNamespaceOperation.class);
  private final AnyNamespaceOperation labeledResourceClientMock =
      mock(AnyNamespaceOperation.class);
  private final SharedIndexInformer informer = mock(SharedIndexInformer.class);
  private final InformerConfiguration<Deployment> informerConfiguration =
      mock(InformerConfiguration.class);

  @BeforeEach
  void setup() {
    when(clientMock.resources(any())).thenReturn(crClientMock);
    when(crClientMock.inAnyNamespace()).thenReturn(specificResourceClientMock);
    when(specificResourceClientMock.withLabelSelector((String) null))
        .thenReturn(labeledResourceClientMock);
    when(labeledResourceClientMock.runnableInformer(0)).thenReturn(informer);
    when(informer.getIndexer()).thenReturn(mock(Indexer.class));

    when(informerConfiguration.getEffectiveNamespaces())
        .thenReturn(DEFAULT_NAMESPACES_SET);
    when(informerConfiguration.getSecondaryToPrimaryMapper())
        .thenReturn(mock(SecondaryToPrimaryMapper.class));
    when(informerConfiguration.getResourceClass()).thenReturn(Deployment.class);

    informerEventSource = new InformerEventSource<>(informerConfiguration, clientMock);
    informerEventSource.setTemporalResourceCache(temporaryResourceCacheMock);
    informerEventSource.setEventHandler(eventHandlerMock);


    SecondaryToPrimaryMapper secondaryToPrimaryMapper = mock(SecondaryToPrimaryMapper.class);
    when(informerConfiguration.getSecondaryToPrimaryMapper())
        .thenReturn(secondaryToPrimaryMapper);
    when(secondaryToPrimaryMapper.toPrimaryResourceIDs(any()))
        .thenReturn(Set.of(ResourceID.fromResource(testDeployment())));
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
  void propagateEventAndRemoveResourceFromTempCacheIfResourceVersionMismatch() {
    Deployment cachedDeployment = testDeployment();
    cachedDeployment.getMetadata().setResourceVersion(PREV_RESOURCE_VERSION);
    when(temporaryResourceCacheMock.getResourceFromCache(any()))
        .thenReturn(Optional.of(cachedDeployment));


    informerEventSource.onUpdate(cachedDeployment, testDeployment());

    verify(eventHandlerMock, times(1)).handleEvent(any());
    verify(temporaryResourceCacheMock, times(1)).removeResourceFromCache(any());
  }

  @Test
  void notPropagatesEventIfAfterUpdateReceivedJustTheRelatedEvent() {
    var testDeployment = testDeployment();
    var prevTestDeployment = testDeployment();
    prevTestDeployment.getMetadata().setResourceVersion(PREV_RESOURCE_VERSION);


    informerEventSource
        .prepareForCreateOrUpdateEventFiltering(ResourceID.fromResource(testDeployment),
            testDeployment);
    informerEventSource.onUpdate(prevTestDeployment, testDeployment);
    informerEventSource.handleRecentResourceUpdate(ResourceID.fromResource(testDeployment),
        testDeployment, prevTestDeployment);

    verify(eventHandlerMock, times(0)).handleEvent(any());
    verify(temporaryResourceCacheMock, times(0)).unconditionallyCacheResource(any());
  }


  @Test
  void notPropagatesEventIfAfterCreateReceivedJustTheRelatedEvent() {
    var testDeployment = testDeployment();

    informerEventSource
        .prepareForCreateOrUpdateEventFiltering(ResourceID.fromResource(testDeployment),
            testDeployment);
    informerEventSource.onAdd(testDeployment);
    informerEventSource.handleRecentResourceCreate(ResourceID.fromResource(testDeployment),
        testDeployment);

    verify(eventHandlerMock, times(0)).handleEvent(any());
    verify(temporaryResourceCacheMock, times(0)).unconditionallyCacheResource(any());
  }

  @Test
  void propagatesEventIfNewEventReceivedAfterTheCurrentTargetEvent() {
    var testDeployment = testDeployment();
    var prevTestDeployment = testDeployment();
    prevTestDeployment.getMetadata().setResourceVersion(PREV_RESOURCE_VERSION);
    var nextTestDeployment = testDeployment();
    nextTestDeployment.getMetadata().setResourceVersion(NEXT_RESOURCE_VERSION);

    informerEventSource
        .prepareForCreateOrUpdateEventFiltering(ResourceID.fromResource(testDeployment),
            testDeployment);
    informerEventSource.onUpdate(prevTestDeployment, testDeployment);
    informerEventSource.onUpdate(testDeployment, nextTestDeployment);
    informerEventSource.handleRecentResourceUpdate(ResourceID.fromResource(testDeployment),
        testDeployment, prevTestDeployment);

    verify(eventHandlerMock, times(1)).handleEvent(any());
    verify(temporaryResourceCacheMock, times(0)).unconditionallyCacheResource(any());
  }

  @Test
  void notPropagatesEventIfMoreReceivedButTheLastIsTheUpdated() {
    var testDeployment = testDeployment();
    var prevTestDeployment = testDeployment();
    prevTestDeployment.getMetadata().setResourceVersion(PREV_RESOURCE_VERSION);
    var prevPrevTestDeployment = testDeployment();
    prevPrevTestDeployment.getMetadata().setResourceVersion("-1");

    informerEventSource
        .prepareForCreateOrUpdateEventFiltering(ResourceID.fromResource(testDeployment),
            testDeployment);
    informerEventSource.onUpdate(prevPrevTestDeployment, prevTestDeployment);
    informerEventSource.onUpdate(prevTestDeployment, testDeployment);
    informerEventSource.handleRecentResourceUpdate(ResourceID.fromResource(testDeployment),
        testDeployment, prevTestDeployment);

    verify(eventHandlerMock, times(0)).handleEvent(any());
    verify(temporaryResourceCacheMock, times(0)).unconditionallyCacheResource(any());
  }

  @Test
  void putsResourceOnTempCacheIfNoEventRecorded() {
    var testDeployment = testDeployment();
    var prevTestDeployment = testDeployment();
    prevTestDeployment.getMetadata().setResourceVersion(PREV_RESOURCE_VERSION);

    informerEventSource
        .prepareForCreateOrUpdateEventFiltering(ResourceID.fromResource(testDeployment),
            testDeployment);
    informerEventSource.handleRecentResourceUpdate(ResourceID.fromResource(testDeployment),
        testDeployment, prevTestDeployment);

    verify(eventHandlerMock, times(0)).handleEvent(any());
    verify(temporaryResourceCacheMock, times(1)).unconditionallyCacheResource(any());
  }

  @Test
  void putsResourceOnTempCacheIfNoEventRecordedWithSameResourceVersion() {
    var testDeployment = testDeployment();
    var prevTestDeployment = testDeployment();
    prevTestDeployment.getMetadata().setResourceVersion(PREV_RESOURCE_VERSION);
    var prevPrevTestDeployment = testDeployment();
    prevPrevTestDeployment.getMetadata().setResourceVersion("-1");

    informerEventSource
        .prepareForCreateOrUpdateEventFiltering(ResourceID.fromResource(testDeployment),
            testDeployment);
    informerEventSource.onUpdate(prevPrevTestDeployment, prevTestDeployment);
    informerEventSource.handleRecentResourceUpdate(ResourceID.fromResource(testDeployment),
        testDeployment, prevTestDeployment);

    verify(eventHandlerMock, times(0)).handleEvent(any());
    verify(temporaryResourceCacheMock, times(1)).unconditionallyCacheResource(any());
  }

  @Test
  void genericFilterForEvents() {
    informerEventSource.setGenericFilter(r -> false);
    when(temporaryResourceCacheMock.getResourceFromCache(any()))
        .thenReturn(Optional.empty());

    informerEventSource.onAdd(testDeployment());
    informerEventSource.onUpdate(testDeployment(), testDeployment());
    informerEventSource.onDelete(testDeployment(), true);

    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void filtersOnAddEvents() {
    informerEventSource.setOnAddFilter(r -> false);
    when(temporaryResourceCacheMock.getResourceFromCache(any()))
        .thenReturn(Optional.empty());

    informerEventSource.onAdd(testDeployment());

    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void filtersOnUpdateEvents() {
    informerEventSource.setOnUpdateFilter((r1, r2) -> false);
    when(temporaryResourceCacheMock.getResourceFromCache(any()))
        .thenReturn(Optional.empty());

    informerEventSource.onUpdate(testDeployment(), testDeployment());

    verify(eventHandlerMock, never()).handleEvent(any());
  }

  @Test
  void filtersOnDeleteEvents() {
    informerEventSource.setOnDeleteFilter((r, b) -> false);
    when(temporaryResourceCacheMock.getResourceFromCache(any()))
        .thenReturn(Optional.empty());

    informerEventSource.onDelete(testDeployment(), true);

    verify(eventHandlerMock, never()).handleEvent(any());
  }

  Deployment testDeployment() {
    Deployment deployment = new Deployment();
    deployment.setMetadata(new ObjectMeta());
    deployment.getMetadata().setResourceVersion(DEFAULT_RESOURCE_VERSION);
    deployment.getMetadata().setName("test");
    return deployment;
  }

}

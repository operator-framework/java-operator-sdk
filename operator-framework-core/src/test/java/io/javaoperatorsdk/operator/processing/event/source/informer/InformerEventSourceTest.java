package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.FilterWatchListMultiDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InformerEventSourceTest {

  private static final String PREV_RESOURCE_VERSION = "0";
  private static final String DEFAULT_RESOURCE_VERSION = "1";
  private static final String NEXT_RESOURCE_VERSION = "2";

  private InformerEventSource<Deployment, TestCustomResource> informerEventSource;
  private KubernetesClient clientMock = mock(KubernetesClient.class);
  private TemporaryResourceCache<Deployment> temporaryResourceCacheMock =
      mock(TemporaryResourceCache.class);
  private EventHandler eventHandlerMock = mock(EventHandler.class);
  private MixedOperation crClientMock = mock(MixedOperation.class);
  private FilterWatchListMultiDeletable specificResourceClientMock =
      mock(FilterWatchListMultiDeletable.class);
  private FilterWatchListDeletable labeledResourceClientMock = mock(FilterWatchListDeletable.class);
  private SharedIndexInformer informer = mock(SharedIndexInformer.class);
  private InformerConfiguration<Deployment, TestCustomResource> informerConfiguration =
      mock(InformerConfiguration.class);

  @BeforeEach
  void setup() {
    when(clientMock.resources(any())).thenReturn(crClientMock);
    when(crClientMock.inAnyNamespace()).thenReturn(specificResourceClientMock);
    when(specificResourceClientMock.withLabelSelector((String) null))
        .thenReturn(labeledResourceClientMock);
    when(labeledResourceClientMock.runnableInformer(0)).thenReturn(informer);

    when(informerConfiguration.getPrimaryResourcesRetriever())
        .thenReturn(mock(PrimaryResourcesRetriever.class));

    informerEventSource = new InformerEventSource<>(informerConfiguration, clientMock);
    informerEventSource.setTemporalResourceCache(temporaryResourceCacheMock);
    informerEventSource.setEventHandler(eventHandlerMock);

    PrimaryResourcesRetriever primaryResourcesRetriever = mock(PrimaryResourcesRetriever.class);
    when(informerConfiguration.getPrimaryResourcesRetriever())
        .thenReturn(primaryResourcesRetriever);
    when(primaryResourcesRetriever.associatedPrimaryResources(any()))
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
        .prepareForCreateOrUpdateEventFiltering(ResourceID.fromResource(testDeployment));
    informerEventSource.onUpdate(prevTestDeployment, testDeployment);
    informerEventSource.handleRecentResourceUpdate(testDeployment, PREV_RESOURCE_VERSION);

    verify(eventHandlerMock, times(0)).handleEvent(any());
    verify(temporaryResourceCacheMock, times(0)).unconditionallyCacheResource(any());
  }


  @Test
  void notPropagatesEventIfAfterCreateReceivedJustTheRelatedEvent() {
    var testDeployment = testDeployment();

    informerEventSource
        .prepareForCreateOrUpdateEventFiltering(ResourceID.fromResource(testDeployment));
    informerEventSource.onAdd(testDeployment);
    informerEventSource.handleRecentResourceCreate(testDeployment);

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
        .prepareForCreateOrUpdateEventFiltering(ResourceID.fromResource(testDeployment));
    informerEventSource.onUpdate(prevTestDeployment, testDeployment);
    informerEventSource.onUpdate(testDeployment, nextTestDeployment);
    informerEventSource.handleRecentResourceUpdate(testDeployment, PREV_RESOURCE_VERSION);

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
        .prepareForCreateOrUpdateEventFiltering(ResourceID.fromResource(testDeployment));
    informerEventSource.onUpdate(prevPrevTestDeployment, prevTestDeployment);
    informerEventSource.onUpdate(prevTestDeployment, testDeployment);
    informerEventSource.handleRecentResourceUpdate(testDeployment, PREV_RESOURCE_VERSION);

    verify(eventHandlerMock, times(0)).handleEvent(any());
    verify(temporaryResourceCacheMock, times(0)).unconditionallyCacheResource(any());
  }

  @Test
  void putsResourceOnTempCacheIfNoEventRecorded() {
    var testDeployment = testDeployment();

    informerEventSource
        .prepareForCreateOrUpdateEventFiltering(ResourceID.fromResource(testDeployment));
    informerEventSource.handleRecentResourceUpdate(testDeployment, PREV_RESOURCE_VERSION);

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
        .prepareForCreateOrUpdateEventFiltering(ResourceID.fromResource(testDeployment));
    informerEventSource.onUpdate(prevPrevTestDeployment, prevTestDeployment);
    informerEventSource.handleRecentResourceUpdate(testDeployment, PREV_RESOURCE_VERSION);

    verify(eventHandlerMock, times(0)).handleEvent(any());
    verify(temporaryResourceCacheMock, times(1)).unconditionallyCacheResource(any());
  }

  Deployment testDeployment() {
    Deployment deployment = new Deployment();
    deployment.setMetadata(new ObjectMeta());
    deployment.getMetadata().setResourceVersion(DEFAULT_RESOURCE_VERSION);
    deployment.getMetadata().setName("test");
    return deployment;
  }

}

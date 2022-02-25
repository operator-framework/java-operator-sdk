package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import static org.mockito.Mockito.*;

class KubernetesDependentResourceTest {

  // private InformerEventSource informerEventSourceMock = mock(InformerEventSource.class);
  // private AssociatedSecondaryResourceIdentifier associatedResourceIdentifierMock =
  // mock(AssociatedSecondaryResourceIdentifier.class);
  // private ResourceMatcher resourceMatcherMock = mock(ResourceMatcher.class);
  // private KubernetesDependentResource.ClientFacade clientFacadeMock =
  // mock(KubernetesDependentResource.ClientFacade.class);
  //
  // KubernetesDependentResource<ConfigMap, TestCustomResource> kubernetesDependentResource =
  // new KubernetesDependentResource() {
  // {
  // this.informerEventSource = informerEventSourceMock;
  // this.resourceMatcher = resourceMatcherMock;
  // this.clientFacade = clientFacadeMock;
  // this.resourceUpdatePreProcessor = mock(ResourceUpdatePreProcessor.class);
  // }
  //
  // @Override
  // protected Object desired(HasMetadata primary, Context context) {
  // return testResource();
  // }
  // };
  //
  // @BeforeEach
  // public void setup() {
  // InformerConfiguration informerConfigurationMock = mock(InformerConfiguration.class);
  // when(informerEventSourceMock.getConfiguration()).thenReturn(informerConfigurationMock);
  // when(informerConfigurationMock.getAssociatedResourceIdentifier())
  // .thenReturn(associatedResourceIdentifierMock);
  // when(associatedResourceIdentifierMock.associatedSecondaryID(any()))
  // .thenReturn(ResourceID.fromResource(testResource()));
  // }
  //
  // @Test
  // void updateCallsInformerJustUpdatedHandler() {
  // when(resourceMatcherMock.match(any(), any(), any())).thenReturn(false);
  // when(clientFacadeMock.replaceResource(any(), any(), any())).thenReturn(testResource());
  // when(informerEventSourceMock.getAssociated(any())).thenReturn(Optional.of(testResource()));
  //
  // kubernetesDependentResource.reconcile(primaryResource(), null);
  //
  // verify(informerEventSourceMock, times(1)).handleRecentResourceUpdate(any(), any());
  // }
  //
  // @Test
  // void createCallsInformerJustUpdatedHandler() {
  // when(clientFacadeMock.createResource(any(), any(), any())).thenReturn(testResource());
  // when(informerEventSourceMock.getAssociated(any())).thenReturn(Optional.empty());
  //
  // kubernetesDependentResource.reconcile(primaryResource(), null);
  //
  // verify(informerEventSourceMock, times(1)).handleRecentResourceAdd(any());
  // }
  //
  // TestCustomResource primaryResource() {
  // TestCustomResource testCustomResource = new TestCustomResource();
  // testCustomResource.setMetadata(new ObjectMeta());
  // testCustomResource.getMetadata().setName("test");
  // testCustomResource.getMetadata().setNamespace("default");
  // return testCustomResource;
  // }
  //
  // ConfigMap testResource() {
  // ConfigMap configMap = new ConfigMap();
  // configMap.setMetadata(new ObjectMeta());
  // configMap.getMetadata().setName("test");
  // configMap.getMetadata().setNamespace("default");
  // configMap.getMetadata().setResourceVersion("0");
  // return configMap;
  // }

}

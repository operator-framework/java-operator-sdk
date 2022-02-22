package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryResourceIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KubernetesDependentResourceTest {

  private InformerEventSource informerEventSourceMock = mock(InformerEventSource.class);
  private AssociatedSecondaryResourceIdentifier associatedResourceIdentifierMock =
      mock(AssociatedSecondaryResourceIdentifier.class);
  private ResourceMatcher resourceMatcherMock = mock(ResourceMatcher.class);
  private KubernetesDependentResource.ClientFacade clientFacadeMock =
      mock(KubernetesDependentResource.ClientFacade.class);

  KubernetesDependentResource<ConfigMap, TestCustomResource> kubernetesDependentResource =
      new KubernetesDependentResource() {
        {
          this.informerEventSource = informerEventSourceMock;
          this.resourceMatcher = resourceMatcherMock;
          this.clientFacade = clientFacadeMock;
          this.resourceUpdatePreProcessor = mock(ResourceUpdatePreProcessor.class);
        }

        @Override
        protected Object desired(HasMetadata primary, Context context) {
          return testResource();
        }
      };

  @BeforeEach
  public void setup() {
    InformerConfiguration informerConfigurationMock = mock(InformerConfiguration.class);
    when(informerEventSourceMock.getConfiguration()).thenReturn(informerConfigurationMock);
    when(informerConfigurationMock.getAssociatedResourceIdentifier())
        .thenReturn(associatedResourceIdentifierMock);
    when(associatedResourceIdentifierMock.associatedSecondaryID(any()))
        .thenReturn(ResourceID.fromResource(testResource()));
  }

  @Test
  void updateCallsInformerJustUpdatedHandler() {
    when(resourceMatcherMock.match(any(), any(), any())).thenReturn(false);
    when(clientFacadeMock.replaceResource(any(), any(), any())).thenReturn(testResource());
    when(informerEventSourceMock.getAssociated(any())).thenReturn(Optional.of(testResource()));

    kubernetesDependentResource.reconcile(primaryResource(), null);

    verify(informerEventSourceMock, times(1)).handleJustUpdatedResource(any(), any());
  }

  @Test
  void createCallsInformerJustUpdatedHandler() {
    when(clientFacadeMock.createResource(any(), any(), any())).thenReturn(testResource());
    when(informerEventSourceMock.getAssociated(any())).thenReturn(Optional.empty());

    kubernetesDependentResource.reconcile(primaryResource(), null);

    verify(informerEventSourceMock, times(1)).handleJustAddedResource(any());
  }

  TestCustomResource primaryResource() {
    TestCustomResource testCustomResource = new TestCustomResource();
    testCustomResource.setMetadata(new ObjectMeta());
    testCustomResource.getMetadata().setName("test");
    testCustomResource.getMetadata().setNamespace("default");
    return testCustomResource;
  }

  ConfigMap testResource() {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMeta());
    configMap.getMetadata().setName("test");
    configMap.getMetadata().setNamespace("default");
    configMap.getMetadata().setResourceVersion("0");
    return configMap;
  }

}

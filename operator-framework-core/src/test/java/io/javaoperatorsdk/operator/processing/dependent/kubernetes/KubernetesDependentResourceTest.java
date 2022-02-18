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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KubernetesDependentResourceTest {

  private TemporalResourceCache temporalResourceCacheMock = mock(TemporalResourceCache.class);
  private InformerEventSource informerEventSourceMock = mock(InformerEventSource.class);
  private AssociatedSecondaryResourceIdentifier associatedResourceIdentifierMock =
      mock(AssociatedSecondaryResourceIdentifier.class);

  KubernetesDependentResource<ConfigMap, TestCustomResource> kubernetesDependentResource =
      new KubernetesDependentResource() {
        {
          this.temporalResourceCache = temporalResourceCacheMock;
          this.informerEventSource = informerEventSourceMock;
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
  void getResourceCheckTheTemporalCacheFirst() {
    when(temporalResourceCacheMock.getResourceFromCache(any()))
        .thenReturn(Optional.of(testResource()));

    kubernetesDependentResource.getResource(primaryResource());

    verify(temporalResourceCacheMock, times(1)).getResourceFromCache(any());
    verify(informerEventSourceMock, never()).get(any());
  }

  @Test
  void getResourceGetsResourceFromInformerIfNotInTemporalCache() {
    var resource = testResource();
    when(temporalResourceCacheMock.getResourceFromCache(any())).thenReturn(Optional.empty());
    when(informerEventSourceMock.get(any())).thenReturn(Optional.of(resource));

    var res = kubernetesDependentResource.getResource(primaryResource());

    verify(temporalResourceCacheMock, times(1)).getResourceFromCache(any());
    verify(informerEventSourceMock, times(1)).get(any());
    assertThat(res.orElseThrow()).isEqualTo(resource);
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

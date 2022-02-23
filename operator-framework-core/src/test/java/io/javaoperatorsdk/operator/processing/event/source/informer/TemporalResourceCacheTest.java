package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TemporalResourceCacheTest {

  public static final String RESOURCE_VERSION = "1";
  private InformerEventSource<ConfigMap, ?> informerEventSource = mock(InformerEventSource.class);
  private TemporalResourceCache<ConfigMap> temporalResourceCache =
      new TemporalResourceCache<>(informerEventSource);


  @Test
  void updateAddsTheResourceIntoCacheIfTheInformerHasThePreviousResourceVersion() {
    var testResource = testResource();
    var prevTestResource = testResource();
    prevTestResource.getMetadata().setResourceVersion("0");
    when(informerEventSource.get(any())).thenReturn(Optional.of(prevTestResource));

    temporalResourceCache.putUpdatedResource(testResource, "0");

    var cached = temporalResourceCache.getResourceFromCache(ResourceID.fromResource(testResource));
    assertThat(cached).isPresent();
  }

  @Test
  void updateNotAddsTheResourceIntoCacheIfTheInformerHasOtherVersion() {
    var testResource = testResource();
    var informerCachedResource = testResource();
    informerCachedResource.getMetadata().setResourceVersion("x");
    when(informerEventSource.get(any())).thenReturn(Optional.of(informerCachedResource));

    temporalResourceCache.putUpdatedResource(testResource, "0");

    var cached = temporalResourceCache.getResourceFromCache(ResourceID.fromResource(testResource));
    assertThat(cached).isNotPresent();
  }

  @Test
  void addOperationAddsTheResourceIfInformerCacheStillEmpty() {
    var testResource = testResource();
    when(informerEventSource.get(any())).thenReturn(Optional.empty());

    temporalResourceCache.putAddedResource(testResource);

    var cached = temporalResourceCache.getResourceFromCache(ResourceID.fromResource(testResource));
    assertThat(cached).isPresent();
  }

  @Test
  void addOperationNotAddsTheResourceIfInformerCacheNotEmpty() {
    var testResource = testResource();
    when(informerEventSource.get(any())).thenReturn(Optional.of(testResource()));

    temporalResourceCache.putAddedResource(testResource);

    var cached = temporalResourceCache.getResourceFromCache(ResourceID.fromResource(testResource));
    assertThat(cached).isNotPresent();
  }

  @Test
  void removesResourceFromCache() {
    ConfigMap testResource = propagateTestResourceToCache();

    temporalResourceCache.removeResourceFromCache(testResource());

    assertThat(temporalResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isNotPresent();
  }

  private ConfigMap propagateTestResourceToCache() {
    var testResource = testResource();
    when(informerEventSource.get(any())).thenReturn(Optional.empty());
    temporalResourceCache.putAddedResource(testResource);
    assertThat(temporalResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isPresent();
    return testResource;
  }

  ConfigMap testResource() {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMeta());
    configMap.getMetadata().setName("test");
    configMap.getMetadata().setNamespace("default");
    configMap.getMetadata().setResourceVersion(RESOURCE_VERSION);
    return configMap;
  }

}

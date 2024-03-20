package io.javaoperatorsdk.operator.processing.event.source.informer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TemporaryResourceCacheTest {

  public static final String RESOURCE_VERSION = "2";
  @SuppressWarnings("unchecked")
  private InformerEventSource<ConfigMap, ?> informerEventSource;
  private TemporaryResourceCache<ConfigMap> temporaryResourceCache;

  @BeforeEach
  void setup() {
    informerEventSource = mock(InformerEventSource.class);
    temporaryResourceCache = new TemporaryResourceCache<>(informerEventSource, false);
  }

  @Test
  void updateAddsTheResourceIntoCacheIfTheInformerHasThePreviousResourceVersion() {
    var testResource = testResource();
    var prevTestResource = testResource();
    prevTestResource.getMetadata().setResourceVersion("0");
    when(informerEventSource.get(any())).thenReturn(Optional.of(prevTestResource));

    temporaryResourceCache.putResource(testResource, "0");

    var cached = temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource));
    assertThat(cached).isPresent();
  }

  @Test
  void updateNotAddsTheResourceIntoCacheIfTheInformerHasOtherVersion() {
    var testResource = testResource();
    var informerCachedResource = testResource();
    informerCachedResource.getMetadata().setResourceVersion("x");
    when(informerEventSource.get(any())).thenReturn(Optional.of(informerCachedResource));

    temporaryResourceCache.putResource(testResource, "0");

    var cached = temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource));
    assertThat(cached).isNotPresent();
  }

  @Test
  void addOperationAddsTheResourceIfInformerCacheStillEmpty() {
    var testResource = testResource();
    when(informerEventSource.get(any())).thenReturn(Optional.empty());

    temporaryResourceCache.putAddedResource(testResource);

    var cached = temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource));
    assertThat(cached).isPresent();
  }

  @Test
  void addOperationNotAddsTheResourceIfInformerCacheNotEmpty() {
    var testResource = testResource();
    when(informerEventSource.get(any())).thenReturn(Optional.of(testResource()));

    temporaryResourceCache.putAddedResource(testResource);

    var cached = temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource));
    assertThat(cached).isNotPresent();
  }

  @Test
  void removesResourceFromCache() {
    ConfigMap testResource = propagateTestResourceToCache();

    temporaryResourceCache.onEvent(testResource(), false);

    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isNotPresent();
  }

  @Test
  void resourceVersionParsing() {
    this.temporaryResourceCache = new TemporaryResourceCache<>(informerEventSource, true);

    assertThat(temporaryResourceCache.isKnownResourceVersion(testResource())).isFalse();

    ConfigMap testResource = propagateTestResourceToCache();

    // an event with a newer version will not remove
    temporaryResourceCache.onEvent(new ConfigMapBuilder(testResource).editMetadata()
        .withResourceVersion("1").endMetadata().build(), false);

    assertThat(temporaryResourceCache.isKnownResourceVersion(testResource)).isTrue();
    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isPresent();

    // anything else will remove
    temporaryResourceCache.onEvent(testResource(), false);

    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isNotPresent();
  }

  private ConfigMap propagateTestResourceToCache() {
    var testResource = testResource();
    when(informerEventSource.get(any())).thenReturn(Optional.empty());
    temporaryResourceCache.putAddedResource(testResource);
    assertThat(temporaryResourceCache.getResourceFromCache(ResourceID.fromResource(testResource)))
        .isPresent();
    return testResource;
  }

  ConfigMap testResource() {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMetaBuilder()
        .withLabels(Map.of("k", "v"))
        .build());
    configMap.getMetadata().setName("test");
    configMap.getMetadata().setNamespace("default");
    configMap.getMetadata().setResourceVersion(RESOURCE_VERSION);
    return configMap;
  }

}

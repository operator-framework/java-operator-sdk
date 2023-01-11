package io.javaoperatorsdk.operator.processing.event.source.cache;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BoundedStoreTest {

  ResourceFetcher<ResourceID, ConfigMap> resourceFetcher = mock(ResourceFetcher.class);
  BoundedCache<ResourceID, ConfigMap> boundedCache = mock(BoundedCache.class);

  BoundedStore<ResourceID, ConfigMap> boundedStore =
      new BoundedStore<>(resourceFetcher, boundedCache);

  @Test
  void storesValue() {
    boundedStore.put(ResourceID.fromResource(testValue1()), testValue1());

    verify(boundedCache, times(1)).put(any(), any());
  }


  @Test
  void fetchesResourceIfNotPresentInCache() {

  }

  @Test
  void removesValueFromCache() {

  }

  @Test
  void listKeys() {

  }


  ConfigMap testValue1() {
    return testValue("test1");
  }

  ConfigMap testValue2() {
    return testValue("test2");
  }

  ConfigMap testValue(String name) {
    var cm = new ConfigMap();
    cm.setMetadata(new ObjectMetaBuilder()
        .withName(name)
        .withNamespace("default")
        .build());
    return cm;
  }
}

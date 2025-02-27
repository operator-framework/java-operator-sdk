package io.javaoperatorsdk.operator.processing.event.source.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.processing.event.source.cache.BoundedItemStore.namespaceKeyFunc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoundedItemStoreTest {

  private BoundedItemStore<TestCustomResource> boundedItemStore;

  @SuppressWarnings("unchecked")
  private final BoundedCache<String, TestCustomResource> boundedCache = mock(BoundedCache.class);

  @SuppressWarnings("unchecked")
  private final ResourceFetcher<String, TestCustomResource> resourceFetcher =
      mock(ResourceFetcher.class);

  @BeforeEach
  void setup() {
    boundedItemStore =
        new BoundedItemStore<>(
            boundedCache, TestCustomResource.class, namespaceKeyFunc(), resourceFetcher);
  }

  @Test
  void shouldNotFetchResourcesFromServerIfNotKnown() {
    var res = boundedItemStore.get(testRes1Key());

    assertThat(res).isNull();
    verify(resourceFetcher, never()).fetchResource(any());
  }

  @Test
  void getsResourceFromServerIfNotInCache() {
    boundedItemStore.put(testRes1Key(), TestUtils.testCustomResource1());
    when(resourceFetcher.fetchResource(testRes1Key())).thenReturn(TestUtils.testCustomResource1());

    var res = boundedItemStore.get(testRes1Key());

    assertThat(res).isNotNull();
    verify(resourceFetcher, times(1)).fetchResource(any());
  }

  @Test
  void removesResourcesNotFoundOnServerFromStore() {
    boundedItemStore.put(testRes1Key(), TestUtils.testCustomResource1());
    when(resourceFetcher.fetchResource(testRes1Key())).thenReturn(null);

    var res = boundedItemStore.get(testRes1Key());

    assertThat(res).isNull();
    assertThat(boundedItemStore.keySet()).isEmpty();
  }

  @Test
  void removesResourceFromCache() {
    boundedItemStore.put(testRes1Key(), TestUtils.testCustomResource1());

    boundedItemStore.remove(testRes1Key());

    var res = boundedItemStore.get(testRes1Key());
    verify(resourceFetcher, never()).fetchResource(any());
    assertThat(res).isNull();
    assertThat(boundedItemStore.keySet()).isEmpty();
  }

  @Test
  void readingKeySetDoesNotReadFromBoundedCache() {
    boundedItemStore.put(testRes1Key(), TestUtils.testCustomResource1());

    boundedItemStore.keySet();

    verify(boundedCache, never()).get(any());
  }

  @Test
  void readingValuesDoesNotReadFromBoundedCache() {
    boundedItemStore.put(testRes1Key(), TestUtils.testCustomResource1());

    boundedItemStore.values();

    verify(boundedCache, never()).get(any());
  }

  String key(HasMetadata r) {
    return Cache.namespaceKeyFunc(r.getMetadata().getNamespace(), r.getMetadata().getName());
  }

  String testRes1Key() {
    return key(TestUtils.testCustomResource1());
  }
}

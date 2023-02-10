package io.javaoperatorsdk.operator.processing.event.source.cache;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.BoundedCacheTestCustomResource;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.BoundedCacheTestReconciler;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.BoundedCacheTestSpec;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import static io.javaoperatorsdk.operator.processing.event.source.cache.sample.BoundedCacheTestReconciler.DATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class CaffeinBoundedCacheIT {

  public static final int NUMBER_OF_RESOURCE_TO_TEST = 3;
  public static final String RESOURCE_NAME_PREFIX = "test-";
  public static final String INITIAL_DATA_PREFIX = "data-";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(new BoundedCacheTestReconciler(), o -> {
        Cache<String, ConfigMap> cache = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.MINUTES)
            .maximumSize(1)
            .build();
        BoundedItemStore<ConfigMap> boundedItemStore =
            new BoundedItemStore<>(new KubernetesClientBuilder().build(), ConfigMap.class,
                new CaffeinBoundedCache<>(cache));
        o.withItemStore(boundedItemStore);
      })
          .build();

  @Test
  void reconciliationWorksWithLimitedCache() {
    createTestResources();

    await().untilAsserted(() -> {
      assertConfigMapData(INITIAL_DATA_PREFIX);
    });
  }

  void assertConfigMapData(String dataPrefix) {
    IntStream.range(0, NUMBER_OF_RESOURCE_TO_TEST).forEach(i -> {
      assertConfigMap(i, dataPrefix);
    });
  }

  private void assertConfigMap(int i, String prefix) {
    var cm = extension.get(ConfigMap.class, RESOURCE_NAME_PREFIX + i);
    assertThat(cm).isNotNull();
    assertThat(cm.getData().get(DATA_KEY)).isEqualTo(prefix + i);
  }

  private void createTestResources() {
    IntStream.range(0, NUMBER_OF_RESOURCE_TO_TEST).forEach(i -> {
      extension.create(createTestResource(i));
    });
  }

  BoundedCacheTestCustomResource createTestResource(int index) {
    var res = new BoundedCacheTestCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName(RESOURCE_NAME_PREFIX + index)
        .build());
    res.setSpec(new BoundedCacheTestSpec());
    res.getSpec().setData(INITIAL_DATA_PREFIX + index);
    return res;
  }

}

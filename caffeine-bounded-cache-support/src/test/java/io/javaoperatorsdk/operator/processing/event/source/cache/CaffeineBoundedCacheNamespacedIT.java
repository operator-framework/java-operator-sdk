package io.javaoperatorsdk.operator.processing.event.source.cache;

import java.time.Duration;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.namespacescope.BoundedCacheTestCustomResource;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.namespacescope.BoundedCacheTestReconciler;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.namespacescope.BoundedCacheTestSpec;

import static io.javaoperatorsdk.operator.processing.event.source.cache.sample.AbstractTestReconciler.boundedItemStore;

class CaffeineBoundedCacheNamespacedIT
    extends BoundedCacheTestBase<BoundedCacheTestCustomResource> {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(
              new BoundedCacheTestReconciler(),
              o -> {
                o.withItemStore(
                    boundedItemStore(
                        new KubernetesClientBuilder().build(),
                        BoundedCacheTestCustomResource.class,
                        Duration.ofMinutes(1),
                        1));
              })
          .build();

  BoundedCacheTestCustomResource createTestResource(int index) {
    var res = new BoundedCacheTestCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME_PREFIX + index).build());
    res.setSpec(new BoundedCacheTestSpec());
    res.getSpec().setData(INITIAL_DATA_PREFIX + index);
    res.getSpec().setTargetNamespace(extension.getNamespace());
    return res;
  }

  @Override
  Class<BoundedCacheTestCustomResource> customResourceClass() {
    return BoundedCacheTestCustomResource.class;
  }

  @Override
  LocallyRunOperatorExtension extension() {
    return extension;
  }
}

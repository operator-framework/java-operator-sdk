package io.javaoperatorsdk.operator.processing.event.source.cache;

import java.time.Duration;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.clusterscope.BoundedCacheClusterScopeTestCustomResource;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.clusterscope.BoundedCacheClusterScopeTestReconciler;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.namespacescope.BoundedCacheTestSpec;

import static io.javaoperatorsdk.operator.processing.event.source.cache.sample.AbstractTestReconciler.boundedItemStore;

public class CaffeineBoundedCacheClusterScopeIT
    extends BoundedCacheTestBase<BoundedCacheClusterScopeTestCustomResource> {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(
              new BoundedCacheClusterScopeTestReconciler(),
              o -> {
                o.withItemStore(
                    boundedItemStore(
                        new KubernetesClientBuilder().build(),
                        BoundedCacheClusterScopeTestCustomResource.class,
                        Duration.ofMinutes(1),
                        1));
              })
          .build();

  @Override
  BoundedCacheClusterScopeTestCustomResource createTestResource(int index) {
    var res = new BoundedCacheClusterScopeTestCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME_PREFIX + index).build());
    res.setSpec(new BoundedCacheTestSpec());
    res.getSpec().setData(INITIAL_DATA_PREFIX + index);
    res.getSpec().setTargetNamespace(extension.getNamespace());
    return res;
  }

  @Override
  Class<BoundedCacheClusterScopeTestCustomResource> customResourceClass() {
    return BoundedCacheClusterScopeTestCustomResource.class;
  }

  @Override
  LocallyRunOperatorExtension extension() {
    return extension;
  }
}

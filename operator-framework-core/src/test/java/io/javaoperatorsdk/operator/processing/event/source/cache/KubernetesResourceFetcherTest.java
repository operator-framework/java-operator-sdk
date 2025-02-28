package io.javaoperatorsdk.operator.processing.event.source.cache;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;

import static org.assertj.core.api.Assertions.assertThat;

class KubernetesResourceFetcherTest {

  public static final String DEFAULT_NAMESPACE = "default";
  public static final String TEST_RESOURCE_NAME = "test1";

  @Test
  void inverseKeyFunction() {
    String key = BoundedItemStore.namespaceKeyFunc().apply(namespacedResource());
    var resourceID = KubernetesResourceFetcher.inverseNamespaceKeyFunction().apply(key);

    assertThat(resourceID.getNamespace()).isPresent().get().isEqualTo(DEFAULT_NAMESPACE);
    assertThat(resourceID.getName()).isEqualTo(TEST_RESOURCE_NAME);

    key = BoundedItemStore.namespaceKeyFunc().apply(clusterScopedResource());
    resourceID = KubernetesResourceFetcher.inverseNamespaceKeyFunction().apply(key);

    assertThat(resourceID.getNamespace()).isEmpty();
    assertThat(resourceID.getName()).isEqualTo(TEST_RESOURCE_NAME);
  }

  private HasMetadata namespacedResource() {
    var cm = new ConfigMap();
    cm.setMetadata(
        new ObjectMetaBuilder()
            .withName(TEST_RESOURCE_NAME)
            .withNamespace(DEFAULT_NAMESPACE)
            .build());
    return cm;
  }

  private HasMetadata clusterScopedResource() {
    var cm = new CustomResourceDefinition();
    cm.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    return cm;
  }
}

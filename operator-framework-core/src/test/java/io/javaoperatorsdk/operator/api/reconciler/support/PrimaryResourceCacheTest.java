package io.javaoperatorsdk.operator.api.reconciler.support;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResourceSpec;

import static org.assertj.core.api.Assertions.assertThat;

class PrimaryResourceCacheTest {

  @Test
  void flowWithResourceVersionParsingEvictionPredicate() {
    var cache =
        new PrimaryResourceCache<TestCustomResource>(
            new PrimaryResourceCache.ResourceVersionParsingEvictionPredicate<>());

    var newCR = customResource("2");
    var cr = cache.getFreshResource(newCR);
    assertThat(cr).isSameAs(newCR);
    // todo break these down by spec
    cache.cacheResource(newCR);
    cr = cache.getFreshResource(customResource("1"));

    assertThat(cr).isSameAs(newCR);

    var newestCR = customResource("3");
    cr = cache.getFreshResource(newestCR);

    assertThat(cr).isSameAs(newestCR);
  }

  @Test
  void customResourceSpecificEvictionPredicate() {
    // todo
  }

  private TestCustomResource customResource(String resourceVersion) {
    var cr = new TestCustomResource();
    cr.setMetadata(
        new ObjectMetaBuilder()
            .withName("test1")
            .withNamespace("default")
            .withUid("uid")
            .withResourceVersion(resourceVersion)
            .build());
    cr.setSpec(new TestCustomResourceSpec());
    cr.getSpec().setKey("key");
    return cr;
  }
}

package io.javaoperatorsdk.operator.api.reconciler.support;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResourceSpec;

import static org.assertj.core.api.Assertions.assertThat;

class PrimaryResourceCacheTest {

  PrimaryResourceCache<TestCustomResource> versionParsingCache =
      new PrimaryResourceCache<>(
          new PrimaryResourceCache.ResourceVersionParsingEvictionPredicate<>());

  @Test
  void returnsThePassedValueIfCacheIsEmpty() {
    var cr = customResource("1");

    var res = versionParsingCache.getFreshResource(cr);

    assertThat(cr).isSameAs(res);
  }

  @Test
  void returnsTheCachedIfNotEvictedAccordingToPredicate() {
    var cr = customResource("2");

    versionParsingCache.cacheResource(cr);

    var res = versionParsingCache.getFreshResource(customResource("1"));
    assertThat(cr).isSameAs(res);
  }

  @Test
  void ifMoreFreshPassedCachedIsEvicted() {
    var cr = customResource("2");
    versionParsingCache.cacheResource(cr);
    var newCR = customResource("3");

    var res = versionParsingCache.getFreshResource(newCR);
    var resOnOlder = versionParsingCache.getFreshResource(cr);

    assertThat(newCR).isSameAs(res);
    assertThat(resOnOlder).isSameAs(cr);
    assertThat(newCR).isNotSameAs(cr);
  }

  @Test
  void cleanupRemovesCachedResources() {
    var cr = customResource("2");
    versionParsingCache.cacheResource(cr);

    versionParsingCache.cleanup(customResource("3"));

    var olderCR = customResource("1");
    var res = versionParsingCache.getFreshResource(olderCR);
    assertThat(olderCR).isSameAs(res);
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

package io.javaoperatorsdk.operator.baseapi.statuscache;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Status patch caching for consistency",
    description =
        "Demonstrates how the framework caches status patches to ensure consistency when status is"
            + " updated frequently. The cache guarantees that status values are monotonically"
            + " increasing and always reflect the most recent state, even with rapid successive"
            + " updates.")
public class StatusPatchCacheIT {

  public static final String TEST_1 = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(StatusPatchCacheReconciler.class)
          .build();

  @Test
  void testStatusAlwaysUpToDate() {
    var reconciler = extension.getReconcilerOfType(StatusPatchCacheReconciler.class);

    extension.create(testResource());

    // the reconciliation is periodically triggered, the status values should be increasing
    // monotonically
    await()
        .pollDelay(Duration.ofSeconds(1))
        .pollInterval(Duration.ofMillis(30))
        .untilAsserted(
            () -> {
              assertThat(reconciler.errorPresent).isFalse();
              assertThat(reconciler.latestValue).isGreaterThan(10);
            });
  }

  StatusPatchCacheCustomResource testResource() {
    var res = new StatusPatchCacheCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_1).build());
    res.setSpec(new StatusPatchCacheSpec());
    return res;
  }
}

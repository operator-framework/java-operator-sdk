package io.javaoperatorsdk.operator.baseapi.statuscache.updatecontrol;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class UpdateControlPrimaryCacheIT {

  public static final String TEST_1 = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(UpdateControlPrimaryReconciler.class)
          .withConfigurationService(
              o ->
                  o.withParseResourceVersions(true)
                      .withGuaranteeUpdatedPrimaryIsAvailableForNextReconciliation(true))
          .build();

  @Test
  void testStatusAlwaysUpToDate() {
    var reconciler = extension.getReconcilerOfType(UpdateControlPrimaryReconciler.class);

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

  UpdateControlPrimaryCacheCustomResource testResource() {
    var res = new UpdateControlPrimaryCacheCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_1).build());
    res.setSpec(new UpdateControlPrimaryCacheSpec());
    return res;
  }
}

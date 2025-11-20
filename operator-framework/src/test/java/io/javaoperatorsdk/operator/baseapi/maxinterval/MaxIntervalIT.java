package io.javaoperatorsdk.operator.baseapi.maxinterval;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Maximum Reconciliation Interval Configuration",
    description =
        "Demonstrates how to configure a maximum interval for periodic reconciliation triggers. The"
            + " test verifies that reconciliation is automatically triggered at the configured"
            + " interval even when there are no resource changes, enabling periodic validation and"
            + " drift detection.")
class MaxIntervalIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(new MaxIntervalTestReconciler()).build();

  @Test
  void reconciliationTriggeredBasedOnMaxInterval() {
    MaxIntervalTestCustomResource cr = createTestResource();

    operator.create(cr);

    await()
        .pollInterval(50, TimeUnit.MILLISECONDS)
        .atMost(500, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        operator
                            .getReconcilerOfType(MaxIntervalTestReconciler.class)
                            .getNumberOfExecutions())
                    .isGreaterThan(3));
  }

  private MaxIntervalTestCustomResource createTestResource() {
    MaxIntervalTestCustomResource cr = new MaxIntervalTestCustomResource();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName("maxintervaltest1");
    return cr;
  }
}

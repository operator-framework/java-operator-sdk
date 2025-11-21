package io.javaoperatorsdk.operator.baseapi.maxintervalafterretry;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Maximum Reconciliation Interval After Retry",
    description =
        """
        Tests that reconciliation is repeatedly triggered based on the maximum interval setting \
        even after retries. This ensures periodic reconciliation continues at the configured \
        maximum interval, maintaining eventual consistency regardless of retry attempts.
        """)
class MaxIntervalAfterRetryIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new MaxIntervalAfterRetryTestReconciler())
          .build();

  @Test
  void reconciliationTriggeredBasedOnMaxInterval() {
    MaxIntervalAfterRetryTestCustomResource cr = createTestResource();

    operator.create(cr);

    await()
        .pollInterval(50, TimeUnit.MILLISECONDS)
        .atMost(1, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        operator
                            .getReconcilerOfType(MaxIntervalAfterRetryTestReconciler.class)
                            .getNumberOfExecutions())
                    .isGreaterThan(5));
  }

  private MaxIntervalAfterRetryTestCustomResource createTestResource() {
    MaxIntervalAfterRetryTestCustomResource cr = new MaxIntervalAfterRetryTestCustomResource();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName("maxintervalretrytest1");
    return cr;
  }
}

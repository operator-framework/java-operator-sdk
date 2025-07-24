package io.javaoperatorsdk.operator.baseapi.maxintervalafterretry;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class MaxIntervalAfterRetryIT {

  @RegisterExtension
  static LocallyRunOperatorExtension operator =
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

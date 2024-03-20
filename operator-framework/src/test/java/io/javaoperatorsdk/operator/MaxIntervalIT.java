package io.javaoperatorsdk.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.maxinterval.MaxIntervalTestCustomResource;
import io.javaoperatorsdk.operator.sample.maxinterval.MaxIntervalTestReconciler;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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
            () -> assertThat(operator.getReconcilerOfType(MaxIntervalTestReconciler.class)
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

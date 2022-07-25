package io.javaoperatorsdk.operator;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.maxinterval.MaxIntervalTestCustomResource;
import io.javaoperatorsdk.operator.sample.maxinterval.MaxIntervalTestReconciler;

import static org.awaitility.Awaitility.await;

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
        .until(
            () -> ((MaxIntervalTestReconciler) operator.getFirstReconciler())
                .getNumberOfExecutions() > 3);
  }

  private MaxIntervalTestCustomResource createTestResource() {
    MaxIntervalTestCustomResource cr = new MaxIntervalTestCustomResource();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName("maxintervaltest1");
    return cr;
  }
}

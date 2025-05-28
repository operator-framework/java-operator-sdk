package io.javaoperatorsdk.operator.baseapi.primarytosecondary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.primarytosecondary.PrimaryToSecondaryIT.cluster;
import static io.javaoperatorsdk.operator.baseapi.primarytosecondary.PrimaryToSecondaryIT.job;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The intention with this IT is to show the use cases why the PrimaryToSecondary Mapper is needed,
 * and the situation when it is not working.
 */
class PrimaryToSecondaryMissingIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withAdditionalCustomResourceDefinition(Cluster.class)
          .withReconciler(new JobReconciler(false))
          .build();

  @Test
  void missingPrimaryToSecondaryCausesIssueAccessingSecondary() throws InterruptedException {
    var reconciler = operator.getReconcilerOfType(JobReconciler.class);
    operator.create(cluster());
    Thread.sleep(300);
    operator.create(job());

    await()
        .untilAsserted(
            () -> {
              assertThat(reconciler.isErrorOccurred()).isTrue();
              assertThat(reconciler.getNumberOfExecutions()).isZero();
            });
  }

  @Test
  void accessingDirectlyTheCacheWorksWithoutPToSMapper() throws InterruptedException {
    var reconciler = operator.getReconcilerOfType(JobReconciler.class);
    reconciler.setGetResourceDirectlyFromCache(true);
    operator.create(cluster());
    Thread.sleep(300);
    operator.create(job());

    await()
        .untilAsserted(
            () -> {
              assertThat(reconciler.isErrorOccurred()).isFalse();
              assertThat(reconciler.getNumberOfExecutions()).isPositive();
            });
  }
}

package io.javaoperatorsdk.operator;

import static io.javaoperatorsdk.operator.PrimaryToSecondaryIT.cluster;
import static io.javaoperatorsdk.operator.PrimaryToSecondaryIT.job;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.primarytosecondary.Cluster;
import io.javaoperatorsdk.operator.sample.primarytosecondary.JobReconciler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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

    await().untilAsserted(() -> {
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

    await().untilAsserted(() -> {
      assertThat(reconciler.isErrorOccurred()).isFalse();
      assertThat(reconciler.getNumberOfExecutions()).isPositive();
    });
  }

}

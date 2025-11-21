package io.javaoperatorsdk.operator.baseapi.primarytosecondary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.primarytosecondary.PrimaryToSecondaryIT.cluster;
import static io.javaoperatorsdk.operator.baseapi.primarytosecondary.PrimaryToSecondaryIT.job;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The intention with this IT is to show the use cases why the PrimaryToSecondary Mapper is needed,
 * and the situation when it is not working.
 */
@Sample(
    tldr = "Issues When Primary-to-Secondary Mapper Is Missing",
    description =
        """
        Demonstrates the problems that occur when accessing secondary resources without a \
        proper PrimaryToSecondaryMapper configured. The test shows that accessing secondary \
        resources through the context fails without the mapper, while direct cache access works \
        as a workaround, highlighting the importance of proper mapper configuration.
        """)
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

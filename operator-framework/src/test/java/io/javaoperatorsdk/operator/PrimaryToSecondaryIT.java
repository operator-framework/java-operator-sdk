package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.primarytosecondary.Cluster;
import io.javaoperatorsdk.operator.sample.primarytosecondary.Job;
import io.javaoperatorsdk.operator.sample.primarytosecondary.JobReconciler;
import io.javaoperatorsdk.operator.sample.primarytosecondary.JobSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class PrimaryToSecondaryIT {

  public static final String CLUSTER_NAME = "cluster1";
  public static final int MIN_DELAY = 150;

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withAdditionalCustomResourceDefinition(Cluster.class)
          .withReconciler(new JobReconciler())
          .build();

  @Test
  void readsSecondaryInManyToOneCases() throws InterruptedException {
    operator.create(cluster());
    Thread.sleep(MIN_DELAY);
    operator.create(job());

    await().pollDelay(Duration.ofMillis(300)).untilAsserted(
        () -> assertThat(operator.getReconcilerOfType(JobReconciler.class).getNumberOfExecutions())
            .isEqualTo(1));
  }

  Job job() {
    var job = new Job();
    job.setMetadata(new ObjectMetaBuilder()
        .withName("job1")
        .build());
    job.setSpec(new JobSpec());
    job.getSpec().setClusterName(CLUSTER_NAME);
    return job;
  }

  Cluster cluster() {
    Cluster cluster = new Cluster();
    cluster.setMetadata(new ObjectMetaBuilder()
        .withName(CLUSTER_NAME)
        .build());
    return cluster;
  }

}

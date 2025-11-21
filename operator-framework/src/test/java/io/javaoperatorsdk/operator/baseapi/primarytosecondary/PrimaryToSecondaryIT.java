package io.javaoperatorsdk.operator.baseapi.primarytosecondary;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Primary to Secondary Resource Mapping",
    description =
        """
        Demonstrates many-to-one mapping between primary and secondary resources where multiple \
        primary resources can reference the same secondary resource. The test verifies that \
        changes in the secondary resource trigger reconciliation of all related primary resources, \
        enabling shared resource patterns.
        """)
class PrimaryToSecondaryIT {

  public static final String CLUSTER_NAME = "cluster1";
  public static final int MIN_DELAY = 150;

  public static final String CLUSTER_VALUE = "clusterValue";
  public static final String JOB_1 = "job1";
  public static final String CHANGED_VALUE = "CHANGED_VALUE";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withAdditionalCustomResourceDefinition(Cluster.class)
          .withReconciler(new JobReconciler())
          .build();

  @Test
  void readsSecondaryInManyToOneCases() throws InterruptedException {
    var cluster = extension.create(cluster());
    Thread.sleep(MIN_DELAY);
    extension.create(job());

    await()
        .pollDelay(Duration.ofMillis(300))
        .untilAsserted(
            () -> {
              assertThat(extension.getReconcilerOfType(JobReconciler.class).getNumberOfExecutions())
                  .isEqualTo(1);
              var job = extension.get(Job.class, JOB_1);
              assertThat(job.getStatus()).isNotNull();
              assertThat(job.getStatus().getValueFromCluster()).isEqualTo(CLUSTER_VALUE);
            });

    cluster.getSpec().setClusterValue(CHANGED_VALUE);
    extension.replace(cluster);

    // cluster change triggers job reconciliations
    await()
        .pollDelay(Duration.ofMillis(300))
        .untilAsserted(
            () -> {
              var job = extension.get(Job.class, JOB_1);
              assertThat(job.getStatus().getValueFromCluster()).isEqualTo(CHANGED_VALUE);
            });
  }

  public static Job job() {
    var job = new Job();
    job.setMetadata(new ObjectMetaBuilder().withName(JOB_1).build());
    job.setSpec(new JobSpec());
    job.getSpec().setClusterName(CLUSTER_NAME);
    return job;
  }

  public static Cluster cluster() {
    Cluster cluster = new Cluster();
    cluster.setMetadata(new ObjectMetaBuilder().withName(CLUSTER_NAME).build());
    cluster.setSpec(new ClusterSpec());
    cluster.getSpec().setClusterValue(CLUSTER_VALUE);
    return cluster;
  }
}

package io.javaoperatorsdk.operator;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.primarytosecondary.Cluster;
import io.javaoperatorsdk.operator.sample.primarytosecondary.JobReconciler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static io.javaoperatorsdk.operator.PrimaryToSecondaryIT.cluster;
import static io.javaoperatorsdk.operator.PrimaryToSecondaryIT.job;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class PrimaryToSecondaryMissingIT {

    @RegisterExtension
    LocallyRunOperatorExtension operator =
            LocallyRunOperatorExtension.builder()
                    .withAdditionalCustomResourceDefinition(Cluster.class)
                    .withReconciler(new JobReconciler(false))
                    .build();

    @Test
    void missingPrimaryToSecondaryCausesIssue() throws InterruptedException {
        var reconciler = operator.getReconcilerOfType(JobReconciler.class);
        operator.create(cluster());
        Thread.sleep(300);
        operator.create(job());

        await().untilAsserted(()->{
            assertThat(reconciler.isErrorOccurred()).isTrue();
            assertThat(reconciler.getNumberOfExecutions()).isZero();
        });
    }

}

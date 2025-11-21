package io.javaoperatorsdk.operator.baseapi.statusupdatelocking;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.statusupdatelocking.StatusUpdateLockingReconciler.WAIT_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Status Update Locking and Concurrency Control",
    description =
        """
        Demonstrates how the framework handles concurrent status updates and ensures no \
        optimistic locking conflicts occur when updating status subresources. The test verifies \
        that status updates can proceed independently of spec updates without causing version \
        conflicts or requiring retries.
        """)
class StatusUpdateLockingIT {

  public static final String TEST_RESOURCE_NAME = "test";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withConfigurationService(o -> o.withUseSSAToPatchPrimaryResource(false))
          .withReconciler(StatusUpdateLockingReconciler.class)
          .build();

  @Test
  void noOptimisticLockingDoneOnStatusPatch() throws InterruptedException {
    var resource = operator.create(createResource());
    Thread.sleep(WAIT_TIME / 2);
    resource.getMetadata().setAnnotations(Map.of("key", "value"));
    operator.replace(resource);

    await()
        .pollDelay(Duration.ofMillis(WAIT_TIME))
        .timeout(Duration.ofSeconds(460))
        .untilAsserted(
            () -> {
              assertThat(
                      operator
                          .getReconcilerOfType(StatusUpdateLockingReconciler.class)
                          .getNumberOfExecutions())
                  .isEqualTo(1);
              assertThat(
                      operator
                          .get(StatusUpdateLockingCustomResource.class, TEST_RESOURCE_NAME)
                          .getStatus()
                          .getValue())
                  .isEqualTo(1);
            });
  }

  StatusUpdateLockingCustomResource createResource() {
    StatusUpdateLockingCustomResource res = new StatusUpdateLockingCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    return res;
  }
}

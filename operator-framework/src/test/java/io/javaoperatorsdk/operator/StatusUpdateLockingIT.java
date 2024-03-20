package io.javaoperatorsdk.operator;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.statusupdatelocking.StatusUpdateLockingCustomResource;
import io.javaoperatorsdk.operator.sample.statusupdatelocking.StatusUpdateLockingReconciler;

import static io.javaoperatorsdk.operator.sample.statusupdatelocking.StatusUpdateLockingReconciler.WAIT_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class StatusUpdateLockingIT {

  public static final String TEST_RESOURCE_NAME = "test";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(StatusUpdateLockingReconciler.class)
          .build();

  @Test
  void optimisticLockingDoneOnStatusUpdate() throws InterruptedException {
    var resource = operator.create(createResource());
    Thread.sleep(WAIT_TIME / 2);
    resource.getMetadata().setAnnotations(Map.of("key", "value"));
    operator.replace(resource);

    await().pollDelay(Duration.ofMillis(WAIT_TIME)).untilAsserted(() -> {
      assertThat(
          operator.getReconcilerOfType(StatusUpdateLockingReconciler.class).getNumberOfExecutions())
          .isEqualTo(2);
      assertThat(operator.get(StatusUpdateLockingCustomResource.class, TEST_RESOURCE_NAME)
          .getStatus().getValue()).isEqualTo(1);
    });
  }

  StatusUpdateLockingCustomResource createResource() {
    StatusUpdateLockingCustomResource res = new StatusUpdateLockingCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    return res;
  }

}

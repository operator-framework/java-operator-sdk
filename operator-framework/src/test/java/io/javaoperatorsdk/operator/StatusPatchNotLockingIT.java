package io.javaoperatorsdk.operator;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.statuspatchnonlocking.StatusPatchLockingCustomResource;
import io.javaoperatorsdk.operator.sample.statuspatchnonlocking.StatusPatchLockingCustomResourceSpec;
import io.javaoperatorsdk.operator.sample.statuspatchnonlocking.StatusPatchLockingReconciler;

import static io.javaoperatorsdk.operator.sample.statuspatchnonlocking.StatusPatchLockingReconciler.MESSAGE;
import static io.javaoperatorsdk.operator.sample.statusupdatelocking.StatusUpdateLockingReconciler.WAIT_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class StatusPatchNotLockingIT {

  public static final String TEST_RESOURCE_NAME = "test";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(StatusPatchLockingReconciler.class)
          .build();

  @Test
  void noOptimisticLockingDoneOnStatusUpdate() throws InterruptedException {
    var resource = operator.create(createResource());
    Thread.sleep(WAIT_TIME / 2);
    resource.getMetadata().setAnnotations(Map.of("key", "value"));
    operator.replace(resource);

    await().pollDelay(Duration.ofMillis(WAIT_TIME)).untilAsserted(() -> {
      assertThat(
          operator.getReconcilerOfType(StatusPatchLockingReconciler.class).getNumberOfExecutions())
          .isEqualTo(1);
      var actual = operator.get(StatusPatchLockingCustomResource.class,
          TEST_RESOURCE_NAME);
      assertThat(actual
          .getStatus().getValue()).isEqualTo(1);
      assertThat(actual.getMetadata().getGeneration())
          .isEqualTo(1);
    });
  }

  // see https://github.com/fabric8io/kubernetes-client/issues/4158
  @Test
  void valuesAreDeletedIfSetToNull() {
    var resource = operator.create(createResource());

    await().untilAsserted(() -> {
      var actual = operator.get(StatusPatchLockingCustomResource.class,
          TEST_RESOURCE_NAME);
      assertThat(actual.getStatus()).isNotNull();
      assertThat(actual.getStatus().getMessage()).isEqualTo(MESSAGE);
    });

    resource.getSpec().setMessageInStatus(false);
    operator.replace(resource);

    await().untilAsserted(() -> {
      var actual = operator.get(StatusPatchLockingCustomResource.class,
          TEST_RESOURCE_NAME);
      assertThat(actual.getStatus()).isNotNull();
      assertThat(actual.getStatus().getMessage()).isNull();
    });
  }

  StatusPatchLockingCustomResource createResource() {
    StatusPatchLockingCustomResource res = new StatusPatchLockingCustomResource();
    res.setSpec(new StatusPatchLockingCustomResourceSpec());
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    return res;
  }
}

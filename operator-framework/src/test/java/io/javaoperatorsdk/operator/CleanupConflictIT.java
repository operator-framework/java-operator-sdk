package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocalOperatorExtension;
import io.javaoperatorsdk.operator.sample.cleanupconflict.CleanupConflictCustomResource;
import io.javaoperatorsdk.operator.sample.cleanupconflict.CleanupConflictReconciler;

import static io.javaoperatorsdk.operator.sample.cleanupconflict.CleanupConflictReconciler.WAIT_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class CleanupConflictIT {

  private static final String ADDITIONAL_FINALIZER = "myfinalizer";
  public static final String TEST_RESOURCE_NAME = "test1";

  @RegisterExtension
  LocalOperatorExtension operator =
      LocalOperatorExtension.builder().withReconciler(new CleanupConflictReconciler())
          .build();

  @Test
  void cleanupRemovesFinalizerWithoutConflict() throws InterruptedException {
    var testResource = operator.create(CleanupConflictCustomResource.class, createTestResource());
    await().untilAsserted(() -> {
      assertThat(operator.getReconcilerOfType(CleanupConflictReconciler.class)
          .getNumberReconcileExecutions()).isEqualTo(1);
    });

    // adding second finalizer later so there is no issue with the remove patch
    // that remove the finalizer by index so order matters
    testResource = operator.get(CleanupConflictCustomResource.class, TEST_RESOURCE_NAME);
    testResource.getMetadata().getFinalizers().add(ADDITIONAL_FINALIZER);
    testResource = operator.replace(CleanupConflictCustomResource.class, testResource);

    operator.delete(CleanupConflictCustomResource.class, testResource);
    Thread.sleep(WAIT_TIME / 2);
    testResource = operator.get(CleanupConflictCustomResource.class, TEST_RESOURCE_NAME);
    testResource.getMetadata().getFinalizers().remove(ADDITIONAL_FINALIZER);
    testResource.getMetadata().setResourceVersion(null);
    operator.replace(CleanupConflictCustomResource.class, testResource);

    await().pollDelay(Duration.ofMillis(WAIT_TIME * 2)).untilAsserted(() -> {
      assertThat(operator.getReconcilerOfType(CleanupConflictReconciler.class)
          .getNumberOfCleanupExecutions()).isEqualTo(1);
    });
  }

  private CleanupConflictCustomResource createTestResource() {
    CleanupConflictCustomResource cr = new CleanupConflictCustomResource();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName(TEST_RESOURCE_NAME);
    return cr;
  }
}

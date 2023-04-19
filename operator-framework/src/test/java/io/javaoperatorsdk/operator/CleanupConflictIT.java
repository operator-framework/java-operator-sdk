package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.jenvtest.junit.EnableKubeAPIServer;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.cleanupconflict.CleanupConflictCustomResource;
import io.javaoperatorsdk.operator.sample.cleanupconflict.CleanupConflictReconciler;

import static io.javaoperatorsdk.operator.sample.cleanupconflict.CleanupConflictReconciler.WAIT_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@EnableKubeAPIServer
class CleanupConflictIT {

  private static final String ADDITIONAL_FINALIZER = "javaoperatorsdk.io/additionalfinalizer";
  public static final String TEST_RESOURCE_NAME = "test1";

  static KubernetesClient client;

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withKubernetesClient(client)
          .waitForNamespaceDeletion(false)
          .withReconciler(new CleanupConflictReconciler())
          .build();

  @Test
  void cleanupRemovesFinalizerWithoutConflict() throws InterruptedException {
    var testResource = createTestResource();
    testResource.addFinalizer(ADDITIONAL_FINALIZER);
    testResource = operator.create(testResource);

    await().untilAsserted(
        () -> assertThat(operator.getReconcilerOfType(CleanupConflictReconciler.class)
            .getNumberReconcileExecutions()).isEqualTo(1));

    operator.delete(testResource);
    Thread.sleep(WAIT_TIME / 2);
    testResource = operator.get(CleanupConflictCustomResource.class, TEST_RESOURCE_NAME);
    testResource.getMetadata().getFinalizers().remove(ADDITIONAL_FINALIZER);
    testResource.getMetadata().setResourceVersion(null);
    operator.replace(testResource);

    await().pollDelay(Duration.ofMillis(WAIT_TIME * 2)).untilAsserted(
        () -> assertThat(operator.getReconcilerOfType(CleanupConflictReconciler.class)
            .getNumberOfCleanupExecutions()).isEqualTo(1));
  }

  private CleanupConflictCustomResource createTestResource() {
    CleanupConflictCustomResource cr = new CleanupConflictCustomResource();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName(TEST_RESOURCE_NAME);
    return cr;
  }
}

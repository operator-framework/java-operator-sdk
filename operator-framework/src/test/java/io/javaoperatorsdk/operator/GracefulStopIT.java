package io.javaoperatorsdk.operator;

import static io.javaoperatorsdk.operator.sample.gracefulstop.GracefulStopTestReconciler.RECONCILER_SLEEP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.gracefulstop.GracefulStopTestCustomResource;
import io.javaoperatorsdk.operator.sample.gracefulstop.GracefulStopTestCustomResourceSpec;
import io.javaoperatorsdk.operator.sample.gracefulstop.GracefulStopTestReconciler;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class GracefulStopIT {

  public static final String TEST_1 = "test1";
  public static final String TEST_2 = "test2";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withConfigurationService(o -> o.withCloseClientOnStop(false))
          .withReconciler(new GracefulStopTestReconciler())
          .build();

  @Test
  void stopsGracefullyWIthTimeout() {
    testGracefulStop(TEST_1, RECONCILER_SLEEP, 2);
  }

  @Test
  void stopsGracefullyWithExpiredTimeout() {
    testGracefulStop(TEST_2, RECONCILER_SLEEP / 5, 1);
  }

  private void testGracefulStop(String resourceName, int stopTimeout, int expectedFinalGeneration) {
    var testRes = operator.create(testResource(resourceName));
    await().untilAsserted(() -> {
      var r = operator.get(GracefulStopTestCustomResource.class, resourceName);
      assertThat(r.getStatus()).isNotNull();
      assertThat(r.getStatus().getObservedGeneration()).isEqualTo(1);
      assertThat(operator.getReconcilerOfType(GracefulStopTestReconciler.class)
          .getNumberOfExecutions()).isEqualTo(1);
    });

    testRes.getSpec().setValue(2);
    operator.replace(testRes);

    await().pollDelay(Duration.ofMillis(50)).untilAsserted(
        () -> assertThat(operator.getReconcilerOfType(GracefulStopTestReconciler.class)
            .getNumberOfExecutions()).isEqualTo(2));

    operator.getOperator().stop(Duration.ofMillis(stopTimeout));

    await().untilAsserted(() -> {
      var r = operator.get(GracefulStopTestCustomResource.class, resourceName);
      assertThat(r.getStatus()).isNotNull();
      assertThat(r.getStatus().getObservedGeneration()).isEqualTo(expectedFinalGeneration);
    });
  }

  public GracefulStopTestCustomResource testResource(String name) {
    GracefulStopTestCustomResource resource =
        new GracefulStopTestCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName(name)
            .withNamespace(operator.getNamespace())
            .build());
    resource.setSpec(new GracefulStopTestCustomResourceSpec());
    resource.getSpec().setValue(1);
    return resource;
  }

}

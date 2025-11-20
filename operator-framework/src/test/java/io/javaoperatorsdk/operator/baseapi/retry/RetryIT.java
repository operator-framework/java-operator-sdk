package io.javaoperatorsdk.operator.baseapi.retry;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.support.TestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Automatic Retry for Failed Reconciliations",
    description =
        "Demonstrates how to configure automatic retry logic for reconciliations that fail"
            + " temporarily. The test shows that failed executions are automatically retried with"
            + " configurable intervals and max attempts. After a specified number of retries, the"
            + " reconciliation succeeds and updates the resource status accordingly.")
class RetryIT {
  public static final int RETRY_INTERVAL = 150;
  public static final int MAX_RETRY_ATTEMPTS = 5;

  public static final int NUMBER_FAILED_EXECUTIONS = 3;

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(
              new RetryTestCustomReconciler(NUMBER_FAILED_EXECUTIONS),
              new GenericRetry()
                  .setInitialInterval(RETRY_INTERVAL)
                  .withLinearRetry()
                  .setMaxAttempts(MAX_RETRY_ATTEMPTS))
          .build();

  @Test
  void retryFailedExecution() {
    RetryTestCustomResource resource = createTestCustomResource("1");

    operator.create(resource);

    await("cr status updated")
        .pollDelay(RETRY_INTERVAL * (NUMBER_FAILED_EXECUTIONS + 2), TimeUnit.MILLISECONDS)
        .pollInterval(RETRY_INTERVAL, TimeUnit.MILLISECONDS)
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              assertThat(TestUtils.getNumberOfExecutions(operator))
                  .isEqualTo(NUMBER_FAILED_EXECUTIONS + 1);

              RetryTestCustomResource finalResource =
                  operator.get(RetryTestCustomResource.class, resource.getMetadata().getName());
              assertThat(finalResource.getStatus().getState())
                  .isEqualTo(RetryTestCustomResourceStatus.State.SUCCESS);
            });
  }

  public static RetryTestCustomResource createTestCustomResource(String id) {
    RetryTestCustomResource resource = new RetryTestCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName("retrysource-" + id).build());
    resource.setKind("retrysample");
    resource.setSpec(new RetryTestCustomResourceSpec());
    resource.getSpec().setValue(id);
    return resource;
  }
}

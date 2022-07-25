package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.sample.retry.RetryTestCustomReconciler;
import io.javaoperatorsdk.operator.sample.retry.RetryTestCustomResource;

import static io.javaoperatorsdk.operator.RetryIT.createTestCustomResource;
import static org.assertj.core.api.Assertions.assertThat;

class RetryMaxAttemptIT {

  public static final int MAX_RETRY_ATTEMPTS = 3;
  public static final int RETRY_INTERVAL = 100;
  public static final int ALL_EXECUTION_TO_FAIL = 99;

  RetryTestCustomReconciler reconciler = new RetryTestCustomReconciler(ALL_EXECUTION_TO_FAIL);

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(reconciler,
              new GenericRetry().setInitialInterval(RETRY_INTERVAL).withLinearRetry()
                  .setMaxAttempts(MAX_RETRY_ATTEMPTS))
          .build();


  @Test
  void retryFailedExecution() throws InterruptedException {
    RetryTestCustomResource resource = createTestCustomResource("max-retry");

    operator.create(resource);

    Thread.sleep((MAX_RETRY_ATTEMPTS + 2) * RETRY_INTERVAL);
    assertThat(reconciler.getNumberOfExecutions()).isEqualTo(4);
  }
}

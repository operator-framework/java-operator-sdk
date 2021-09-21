package io.javaoperatorsdk.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.sample.retry.RetryTestCustomResource;
import io.javaoperatorsdk.operator.sample.retry.RetryTestCustomResourceController;
import io.javaoperatorsdk.operator.sample.retry.RetryTestCustomResourceSpec;
import io.javaoperatorsdk.operator.sample.retry.RetryTestCustomResourceStatus;
import io.javaoperatorsdk.operator.support.TestUtils;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class RetryIT {
  public static final int RETRY_INTERVAL = 150;

  @RegisterExtension
  OperatorExtension operator =
      OperatorExtension.builder()
          .withConfigurationService(DefaultConfigurationService.instance())
          .withController(
              new RetryTestCustomResourceController(),
              new GenericRetry().setInitialInterval(RETRY_INTERVAL).withLinearRetry()
                  .setMaxAttempts(5))
          .build();


  @Test
  public void retryFailedExecution() {
    RetryTestCustomResource resource = createTestCustomResource("1");

    operator.create(RetryTestCustomResource.class, resource);

    await("cr status updated")
        .pollDelay(
            RETRY_INTERVAL * (RetryTestCustomResourceController.NUMBER_FAILED_EXECUTIONS + 2),
            TimeUnit.MILLISECONDS)
        .pollInterval(
            RETRY_INTERVAL,
            TimeUnit.MILLISECONDS)
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          assertThat(
              TestUtils.getNumberOfExecutions(operator))
                  .isEqualTo(RetryTestCustomResourceController.NUMBER_FAILED_EXECUTIONS + 1);

          RetryTestCustomResource finalResource =
              operator.getNamedResource(RetryTestCustomResource.class,
                  resource.getMetadata().getName());
          assertThat(finalResource.getStatus().getState())
              .isEqualTo(RetryTestCustomResourceStatus.State.SUCCESS);
        });
  }

  public RetryTestCustomResource createTestCustomResource(String id) {
    RetryTestCustomResource resource = new RetryTestCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName("retrysource-" + id)
            .withFinalizers(RetryTestCustomResourceController.FINALIZER_NAME)
            .build());
    resource.setKind("retrysample");
    resource.setSpec(new RetryTestCustomResourceSpec());
    resource.getSpec().setValue(id);
    return resource;
  }
}

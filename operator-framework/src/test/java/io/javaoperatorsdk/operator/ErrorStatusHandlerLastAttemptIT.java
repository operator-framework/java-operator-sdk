package io.javaoperatorsdk.operator;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.sample.errorstatushandler.ErrorStatusHandlerTestCustomResource;
import io.javaoperatorsdk.operator.sample.errorstatushandler.ErrorStatusHandlerTestReconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ErrorStatusHandlerLastAttemptIT {

  public static final int MAX_RETRY_ATTEMPTS = 0;
  ErrorStatusHandlerTestReconciler reconciler = new ErrorStatusHandlerTestReconciler();

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(reconciler,
              new GenericRetry().setMaxAttempts(MAX_RETRY_ATTEMPTS).withLinearRetry())
          .build();

  @Test
  void testErrorMessageSetEventually() {
    ErrorStatusHandlerTestCustomResource resource =
        operator.create(ErrorStatusHandlerTestCustomResource.class, createCustomResource());

    await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(250, TimeUnit.MICROSECONDS)
        .untilAsserted(
            () -> {
              ErrorStatusHandlerTestCustomResource res =
                  operator.get(ErrorStatusHandlerTestCustomResource.class,
                      resource.getMetadata().getName());
              assertThat(res.getStatus()).isNotNull();
              assertThat(res.getStatus().getMessages())
                  .contains(ErrorStatusHandlerTestReconciler.ERROR_STATUS_MESSAGE + 0 + true);
            });
  }

  public ErrorStatusHandlerTestCustomResource createCustomResource() {
    ErrorStatusHandlerTestCustomResource resource = new ErrorStatusHandlerTestCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName("error-status-test")
            .withNamespace(operator.getNamespace())
            .build());
    return resource;
  }

}

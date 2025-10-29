/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.baseapi.errorstatushandler;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ErrorStatusHandlerIT {

  public static final int MAX_RETRY_ATTEMPTS = 3;
  ErrorStatusHandlerTestReconciler reconciler = new ErrorStatusHandlerTestReconciler();

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(
              reconciler, new GenericRetry().setMaxAttempts(MAX_RETRY_ATTEMPTS).withLinearRetry())
          .build();

  @Test
  void testErrorMessageSetEventually() {
    ErrorStatusHandlerTestCustomResource resource = operator.create(createCustomResource());

    await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(250, TimeUnit.MICROSECONDS)
        .untilAsserted(
            () -> {
              ErrorStatusHandlerTestCustomResource res =
                  operator.get(
                      ErrorStatusHandlerTestCustomResource.class, resource.getMetadata().getName());
              assertThat(res.getStatus()).isNotNull();
              for (int i = 0; i < MAX_RETRY_ATTEMPTS + 1; i++) {
                assertThat(res.getStatus().getMessages())
                    .contains(ErrorStatusHandlerTestReconciler.ERROR_STATUS_MESSAGE + i);
              }
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

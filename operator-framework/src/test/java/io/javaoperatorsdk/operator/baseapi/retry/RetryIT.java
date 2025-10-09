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
package io.javaoperatorsdk.operator.baseapi.retry;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.support.TestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;

import static io.javaoperatorsdk.operator.baseapi.retry.RetryIT.createTestCustomResource;
import static org.assertj.core.api.Assertions.assertThat;

class RetryMaxAttemptIT {

  public static final int MAX_RETRY_ATTEMPTS = 3;
  public static final int RETRY_INTERVAL = 100;
  public static final int ALL_EXECUTION_TO_FAIL = 99;

  RetryTestCustomReconciler reconciler = new RetryTestCustomReconciler(ALL_EXECUTION_TO_FAIL);

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(
              reconciler,
              new GenericRetry()
                  .setInitialInterval(RETRY_INTERVAL)
                  .withLinearRetry()
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

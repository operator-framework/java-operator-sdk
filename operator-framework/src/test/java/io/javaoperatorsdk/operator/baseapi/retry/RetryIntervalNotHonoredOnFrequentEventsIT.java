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

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;

import static io.javaoperatorsdk.operator.baseapi.retry.RetryIT.createTestCustomResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RetryIntervalNotHonoredOnFrequentEventsIT {

  private static final Logger log =
      LoggerFactory.getLogger(RetryIntervalNotHonoredOnFrequentEventsIT.class);

  public static final int MAX_RETRY_ATTEMPTS = 3;
  public static final int RETRY_INTERVAL_MILLIS = 60_000;
  public static final int ALL_EXECUTIONS_TO_FAIL = 99;
  public static final int NUMBER_OF_UPDATES = 5;

  RetryTestCustomReconciler reconciler = new RetryTestCustomReconciler(ALL_EXECUTIONS_TO_FAIL);

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(
              reconciler,
              new GenericRetry()
                  .setInitialInterval(RETRY_INTERVAL_MILLIS)
                  .withLinearRetry()
                  .setMaxAttempts(MAX_RETRY_ATTEMPTS))
          .build();

  @Test
  void frequentEventsDuringRetryWindowExhaustRetryCounter() {
    RetryTestCustomResource resource = createTestCustomResource("frequent-events");
    var created = operator.create(resource);

    // Wait until the initial reconciliation has been executed and failed; the retry timer is now
    // armed for RETRY_INTERVAL_MILLIS in the future.
    await()
        .pollInterval(Duration.ofMillis(50))
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> assertThat(reconciler.getNumberOfExecutions()).isGreaterThanOrEqualTo(1));

    // Trigger several updates, waiting for each to result in its own reconciliation cycle. Without
    // this spacing, multiple events would collapse into a single reconciliation and would not
    // advance the retry counter on every update. With proper spacing, each failed reconciliation
    // advances the retry counter — even though we are well inside the configured 1 minute
    // interval and the retry timer hasn't fired yet.
    IntStream.rangeClosed(1, NUMBER_OF_UPDATES)
        .forEach(
            i -> {
              log.debug("replacing resource, iteration: {}", i);
              var latest =
                  operator.get(RetryTestCustomResource.class, created.getMetadata().getName());
              latest.getSpec().setValue("update-" + i);
              operator.replace(latest);
              int expectedExecutions = i + 1;
              await()
                  .pollInterval(Duration.ofMillis(50))
                  .atMost(5, TimeUnit.SECONDS)
                  .untilAsserted(
                      () ->
                          assertThat(reconciler.getNumberOfExecutions())
                              .isGreaterThanOrEqualTo(expectedExecutions));
            });

    // We reached at least MAX_RETRY_ATTEMPTS + 1 executions (initial + 3 retries) within seconds,
    // even though the configured retry interval is 1 minute. With the interval being honored we
    // would expect at most 1 reconciliation in this window.
    assertThat(reconciler.getNumberOfExecutions()).isGreaterThanOrEqualTo(MAX_RETRY_ATTEMPTS + 1);
  }
}

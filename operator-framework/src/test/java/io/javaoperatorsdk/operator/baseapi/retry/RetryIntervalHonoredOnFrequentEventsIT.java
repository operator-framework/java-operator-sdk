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

@Sample(
    tldr = "Retry Interval Honored Despite Frequent Reconciliation Triggers",
    description =
        """
        Verifies that with a low max attempts (3) and a high retry interval (1 minute), \
        reconciliations triggered by external events (e.g. resource updates) during the retry \
        window do not consume retry attempts. The retry counter should only advance when the \
        scheduled retry deadline is approached, so the configured interval is honored.
        """)
class RetryIntervalHonoredOnFrequentEventsIT {

  private static final Logger log =
      LoggerFactory.getLogger(RetryIntervalHonoredOnFrequentEventsIT.class);

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
  void frequentEventsDuringRetryWindowDoNotExhaustRetryCounter() {
    RetryTestCustomResource resource = createTestCustomResource("frequent-events");
    var created = operator.create(resource);

    // Wait until the initial reconciliation has been executed and failed; the retry timer is now
    // armed for RETRY_INTERVAL_MILLIS in the future, retry counter is at 1.
    await()
        .pollInterval(Duration.ofMillis(50))
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> assertThat(reconciler.getNumberOfExecutions()).isGreaterThanOrEqualTo(1));

    // Trigger several updates spaced apart so each results in its own reconciliation cycle. Each
    // failed reconciliation lands well inside the 1 minute retry window, so the retry counter
    // must NOT advance — only the original retry deadline matters.
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

    // Reconciliations did happen for every event (so events are not lost) but the retry counter
    // observed inside the reconciler never went past 1: the configured 1 minute interval is
    // honored even under a steady stream of external events.
    assertThat(reconciler.getNumberOfExecutions()).isGreaterThanOrEqualTo(NUMBER_OF_UPDATES + 1);
    assertThat(reconciler.getMaxObservedRetryAttempt()).isEqualTo(1);
  }
}

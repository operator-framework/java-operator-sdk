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
package io.javaoperatorsdk.operator.api.config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Throwaway audit tests for VirtualThreads.BoundedExecutorService. */
class BoundedVirtualThreadExecutorAuditTest {

  @Test
  void controlPlatformPoolRejectsNonPositiveConcurrency() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ExecutorServiceManager.newBoundedExecutorService(0, false));
  }

  /**
   * The platform variant fails fast for a non positive limit, the virtual variant creates a
   * Semaphore(0) (or a negative one) whose tasks wait for a permit forever.
   */
  @Test
  @EnabledForJreRange(min = JRE.JAVA_25)
  void virtualThreadVariantRejectsNonPositiveConcurrencyToo() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ExecutorServiceManager.newBoundedExecutorService(0, true).shutdownNow());
  }

  @Test
  void controlPlatformPoolShutdownNowReturnsTasksThatNeverStarted() throws Exception {
    assertThat(queuedTasksReturnedByShutdownNow(false)).isEqualTo(5);
  }

  /**
   * ExecutorService#shutdownNow must return the tasks that never commenced execution; the bounded
   * virtual executor returns an empty list, and plain Runnables handed to execute() that were still
   * waiting for a permit are silently dropped (neither run nor returned).
   */
  @Test
  @EnabledForJreRange(min = JRE.JAVA_25)
  void virtualThreadVariantShutdownNowReturnsTasksThatNeverStarted() throws Exception {
    assertThat(queuedTasksReturnedByShutdownNow(true)).isEqualTo(5);
  }

  @Test
  @EnabledForJreRange(min = JRE.JAVA_25)
  void everyTaskSubmittedWhileShuttingDownIsRunReturnedOrRejected() throws Exception {
    for (int round = 0; round < 50; round++) {
      final var executor = ExecutorServiceManager.newBoundedExecutorService(1, true);
      final var accepted = new AtomicInteger();
      final var ran = new AtomicInteger();
      final var submitters = new CountDownLatch(4);
      final var go = new CountDownLatch(1);
      for (int t = 0; t < 4; t++) {
        new Thread(
                () -> {
                  try {
                    go.await();
                    for (int i = 0; i < 200; i++) {
                      executor.execute(ran::incrementAndGet);
                      accepted.incrementAndGet();
                    }
                  } catch (RejectedExecutionException | InterruptedException e) {
                    // the executor was shut down
                  } finally {
                    submitters.countDown();
                  }
                })
            .start();
      }
      go.countDown();
      final var notStarted = executor.shutdownNow();
      assertThat(submitters.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
      // tasks admitted after shutdownNow returned can't exist, so none may go missing
      assertThat(ran.get() + notStarted.size()).isEqualTo(accepted.get());
    }
  }

  private static int queuedTasksReturnedByShutdownNow(boolean useVirtualThreads) throws Exception {
    final var executor = ExecutorServiceManager.newBoundedExecutorService(1, useVirtualThreads);
    final var running = new CountDownLatch(1);
    final var ran = new AtomicInteger();
    executor.execute(
        () -> {
          running.countDown();
          try {
            Thread.sleep(60_000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
    assertThat(running.await(10, TimeUnit.SECONDS)).isTrue();
    for (int i = 0; i < 5; i++) {
      executor.execute(ran::incrementAndGet);
    }
    Thread.sleep(200); // let the virtual threads reach the permit acquisition
    final var notStarted = executor.shutdownNow();
    assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    assertThat(ran).hasValue(0);
    return notStarted.size();
  }
}

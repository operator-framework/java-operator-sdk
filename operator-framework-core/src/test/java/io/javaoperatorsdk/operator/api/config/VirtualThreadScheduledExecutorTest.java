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

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;

@EnabledForJreRange(min = JRE.JAVA_25)
class VirtualThreadScheduledExecutorTest {

  private static final int MAX_CONCURRENCY = 2;
  private static final int TASKS = 10;

  private ScheduledExecutorService executor;

  @BeforeEach
  void setUp() {
    executor = ExecutorServiceManager.newScheduledExecutorService(MAX_CONCURRENCY, true, "test");
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void runsDelayedTasksOnVirtualThreads() throws Exception {
    var future = executor.schedule(VirtualThreadsTest::onVirtualThread, 50, TimeUnit.MILLISECONDS);

    assertThat(future.get(10, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void keepsTheTimingOnADaemonPlatformThread() {
    executor.schedule(() -> {}, 10, TimeUnit.MINUTES);

    assertThat(Thread.getAllStackTraces().keySet())
        .filteredOn(t -> t.getName().startsWith("test-timer"))
        .isNotEmpty()
        .allSatisfy(t -> assertThat(t.isDaemon()).isTrue());
  }

  @Test
  void delayedTasksDoNotRunEarly() throws Exception {
    var start = System.nanoTime();

    executor.schedule(() -> {}, 200, TimeUnit.MILLISECONDS).get(10, TimeUnit.SECONDS);

    assertThat(Duration.ofNanos(System.nanoTime() - start))
        .isGreaterThanOrEqualTo(Duration.ofMillis(190));
  }

  @Test
  void cancellingBeforeTheTaskFiresPreventsIt() throws Exception {
    var ran = new AtomicBoolean();
    var future = executor.schedule(() -> ran.set(true), 200, TimeUnit.MILLISECONDS);

    assertThat(future.cancel(false)).isTrue();

    assertThat(future.isCancelled()).isTrue();
    assertThatExceptionOfType(CancellationException.class).isThrownBy(future::get);
    Thread.sleep(400);
    assertThat(ran).isFalse();
  }

  @Test
  void propagatesTheFailureOfATask() {
    var future =
        executor.schedule(
            () -> {
              throw new IllegalStateException("failed");
            },
            0,
            TimeUnit.MILLISECONDS);

    assertThatExceptionOfType(ExecutionException.class)
        .isThrownBy(() -> future.get(10, TimeUnit.SECONDS))
        .withCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void repeatsTasksWithAFixedDelayUntilCancelled() {
    var runs = new AtomicInteger();
    var onVirtualThread = new AtomicBoolean(true);

    var future =
        executor.scheduleWithFixedDelay(
            () -> {
              onVirtualThread.compareAndSet(true, VirtualThreadsTest.onVirtualThread());
              runs.incrementAndGet();
            },
            0,
            20,
            TimeUnit.MILLISECONDS);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(runs).hasValueGreaterThan(3));
    assertThat(future.cancel(false)).isTrue();
    var runsAtCancel = runs.get();
    await()
        .pollDelay(Duration.ofMillis(200))
        .untilAsserted(() -> assertThat(runs.get()).isLessThanOrEqualTo(runsAtCancel + 1));
    assertThat(onVirtualThread).isTrue();
  }

  @Test
  void repeatsTasksAtAFixedRate() {
    var runs = new AtomicInteger();

    executor.scheduleAtFixedRate(runs::incrementAndGet, 0, 20, TimeUnit.MILLISECONDS);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(runs).hasValueGreaterThan(3));
  }

  @Test
  void aFixedRateTaskCatchesUpAfterAnOverrun() {
    var starts = new CopyOnWriteArrayList<Long>();
    var firstRunEnd = new AtomicLong();

    executor.scheduleAtFixedRate(
        () -> {
          starts.add(System.nanoTime());
          if (starts.size() == 1) {
            try {
              Thread.sleep(350);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            firstRunEnd.set(System.nanoTime());
          }
        },
        0,
        100,
        TimeUnit.MILLISECONDS);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(starts).hasSizeGreaterThanOrEqualTo(4));
    // the three executions missed during the first run are made up for right after it, instead of
    // being skipped, which would space the fourth one a full period behind the third
    assertThat(Duration.ofNanos(starts.get(3) - firstRunEnd.get()))
        .isLessThan(Duration.ofMillis(150));
  }

  @Test
  void aFailingPeriodicTaskIsNotRepeated() {
    var runs = new AtomicInteger();

    var future =
        executor.scheduleWithFixedDelay(
            () -> {
              runs.incrementAndGet();
              throw new IllegalStateException("failed");
            },
            0,
            20,
            TimeUnit.MILLISECONDS);

    assertThatExceptionOfType(ExecutionException.class)
        .isThrownBy(() -> future.get(10, TimeUnit.SECONDS));
    await().pollDelay(Duration.ofMillis(200)).untilAsserted(() -> assertThat(runs).hasValue(1));
  }

  @Test
  void doesNotLimitTheNumberOfTasksRunningAtTheSameTime() throws Exception {
    var allStarted = new CountDownLatch(TASKS);
    var done = new CountDownLatch(TASKS);
    for (int i = 0; i < TASKS; i++) {
      executor.schedule(
          () -> {
            allStarted.countDown();
            try {
              // only returns if all the tasks are running at the same time, which with more tasks
              // than the (platform thread) concurrency would never be the case if it was limited
              if (allStarted.await(30, TimeUnit.SECONDS)) {
                done.countDown();
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          },
          0,
          TimeUnit.MILLISECONDS);
    }

    assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void shutdownDiscardsTasksScheduledForLaterAndRejectsNewOnes() throws Exception {
    var ran = new AtomicBoolean();
    var later = executor.schedule(() -> ran.set(true), 10, TimeUnit.MINUTES);
    var periodic = executor.scheduleWithFixedDelay(() -> {}, 10, 10, TimeUnit.MINUTES);

    executor.shutdown();

    assertThat(later.isCancelled()).isTrue();
    assertThat(periodic.isCancelled()).isTrue();
    assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    assertThat(ran).isFalse();
    assertThatExceptionOfType(RejectedExecutionException.class)
        .isThrownBy(() -> executor.schedule(() -> {}, 0, TimeUnit.MILLISECONDS));
  }

  @Test
  void shutdownLetsStartedTasksFinish() throws Exception {
    var started = new CountDownLatch(1);
    var finished = new AtomicBoolean();
    var future =
        executor.schedule(
            () -> {
              started.countDown();
              Thread.sleep(200);
              finished.set(true);
              return true;
            },
            0,
            TimeUnit.MILLISECONDS);
    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();

    executor.shutdown();

    assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    assertThat(future.get()).isTrue();
    assertThat(finished).isTrue();
  }

  @Test
  void shutdownNowInterruptsRunningTasks() throws Exception {
    var started = new CountDownLatch(1);
    var interrupted = new CountDownLatch(1);
    executor.schedule(
        () -> {
          started.countDown();
          try {
            Thread.sleep(60_000);
          } catch (InterruptedException e) {
            interrupted.countDown();
          }
        },
        0,
        TimeUnit.MILLISECONDS);
    assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();

    executor.shutdownNow();

    assertThat(interrupted.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void theOperatorsDefaultScheduledExecutorsRunOnVirtualThreadsWhenRequested() throws Exception {
    var configurationService =
        ConfigurationService.newOverriddenConfigurationService(
            new BaseConfigurationService(), o -> o.withUseVirtualThreads(true));
    var manager = configurationService.getExecutorServiceManager();
    try {
      assertThat(
              manager
                  .scheduledExecutorService()
                  .schedule(VirtualThreadsTest::onVirtualThread, 0, TimeUnit.MILLISECONDS)
                  .get(10, TimeUnit.SECONDS))
          .isTrue();
      assertThat(
              manager
                  .retryAndRescheduleExecutorService()
                  .schedule(VirtualThreadsTest::onVirtualThread, 0, TimeUnit.MILLISECONDS)
                  .get(10, TimeUnit.SECONDS))
          .isTrue();
    } finally {
      manager.stop(Duration.ofMillis(100));
    }
  }
}

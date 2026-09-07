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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static org.assertj.core.api.Assertions.assertThat;

class VirtualThreadsTest {

  private static final int MAX_CONCURRENCY = 3;
  private static final int TASK_NUMBER = 20;
  private static final int TIMEOUT_SECONDS = 30;

  @Test
  void usesPlatformThreadPoolWhenVirtualThreadsAreNotRequested() throws Exception {
    var executor = ExecutorServiceManager.newBoundedExecutorService(MAX_CONCURRENCY, false);
    try {
      assertThat(executor).isInstanceOf(ThreadPoolExecutor.class);
      assertThat(executor.submit(VirtualThreadsTest::onVirtualThread).get()).isFalse();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @EnabledForJreRange(min = JRE.JAVA_21)
  void usesVirtualThreadsWhenRequested() throws Exception {
    var virtualExecutor = ExecutorServiceManager.newBoundedExecutorService(MAX_CONCURRENCY, true);
    try {
      assertThat(virtualExecutor.submit(VirtualThreadsTest::onVirtualThread).get()).isTrue();
    } finally {
      virtualExecutor.shutdownNow();
    }

    var unbounded = ExecutorServiceManager.newUnboundedExecutorService(true);
    try {
      assertThat(unbounded.submit(VirtualThreadsTest::onVirtualThread).get()).isTrue();
    } finally {
      unbounded.shutdownNow();
    }
  }

  @Test
  @EnabledForJreRange(min = JRE.JAVA_21)
  void boundedVirtualThreadExecutorRespectsTheConfiguredConcurrency() throws Exception {
    var executor = ExecutorServiceManager.newBoundedExecutorService(MAX_CONCURRENCY, true);
    try {
      final var running = new AtomicInteger();
      final var maxObservedConcurrency = new AtomicInteger();
      final var done = new CountDownLatch(TASK_NUMBER);
      // each task gets its own (virtual) thread, only the number of concurrently running ones is
      // capped
      final Set<Thread> usedThreads = ConcurrentHashMap.newKeySet();

      IntStream.range(0, TASK_NUMBER)
          .forEach(
              i ->
                  executor.execute(
                      () -> {
                        usedThreads.add(Thread.currentThread());
                        maxObservedConcurrency.accumulateAndGet(
                            running.incrementAndGet(), Math::max);
                        try {
                          Thread.sleep(50);
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                        } finally {
                          running.decrementAndGet();
                          done.countDown();
                        }
                      }));

      assertThat(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
      assertThat(maxObservedConcurrency).hasValue(MAX_CONCURRENCY);
      assertThat(usedThreads).hasSize(TASK_NUMBER);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @EnabledForJreRange(min = JRE.JAVA_21)
  void invokeAllIsBoundedTooSinceItIsUsedToStartTheEventSources() {
    var executor = ExecutorServiceManager.newBoundedExecutorService(MAX_CONCURRENCY, true);
    try {
      final var running = new AtomicInteger();
      final var maxObservedConcurrency = new AtomicInteger();

      ExecutorServiceManager.executeAndWaitForAllToComplete(
          IntStream.range(0, TASK_NUMBER).boxed(),
          i -> {
            maxObservedConcurrency.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
              Thread.sleep(50);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              running.decrementAndGet();
            }
            return null;
          },
          i -> "task-" + i,
          executor);

      assertThat(maxObservedConcurrency).hasValue(MAX_CONCURRENCY);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @EnabledForJreRange(min = JRE.JAVA_21)
  void shutdownTerminatesOnceTheAlreadySubmittedTasksAreDone() throws Exception {
    ExecutorService executor =
        ExecutorServiceManager.newBoundedExecutorService(MAX_CONCURRENCY, true);
    final var done = new CountDownLatch(TASK_NUMBER);

    IntStream.range(0, TASK_NUMBER).forEach(i -> executor.execute(done::countDown));
    executor.shutdown();

    assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    assertThat(executor.isShutdown()).isTrue();
    assertThat(done.getCount()).isZero();
  }

  /**
   * {@code Thread.isVirtual} only exists as of Java 21 while the tests are compiled for Java 17,
   * hence the reflective call.
   */
  static boolean onVirtualThread() {
    try {
      return (Boolean) Thread.class.getMethod("isVirtual").invoke(Thread.currentThread());
    } catch (ReflectiveOperationException e) {
      return false;
    }
  }
}

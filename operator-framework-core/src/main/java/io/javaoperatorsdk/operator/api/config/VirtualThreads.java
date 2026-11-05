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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.KubernetesClientBuilder.ExecutorSupplier;
import io.javaoperatorsdk.operator.OperatorException;

/**
 * Creates the virtual thread based executors used when {@link
 * ConfigurationService#useVirtualThreads()} is enabled.
 *
 * <p>Virtual threads are only supported on Java 25 or later, where <a
 * href="https://openjdk.org/jeps/491">JEP 491</a> removed the pinning of the carrier thread by
 * {@code synchronized} blocks. On older JVMs, including the ones where virtual threads already
 * exist, the request is ignored with a warning and platform threads are used, see {@link
 * #shouldUse(boolean)}.
 *
 * <p>The SDK is compiled for Java 17, in which virtual threads don't exist yet, so {@code
 * Executors.newVirtualThreadPerTaskExecutor()} is looked up reflectively.
 *
 * <p>The JDK provides no virtual thread backed {@link
 * java.util.concurrent.ScheduledExecutorService} (the Loom runtime itself uses a platform thread
 * scheduler to unpark timed out virtual threads), so the scheduled variant, see {@link
 * #newScheduledExecutor(String)}, keeps a single platform thread for the timing only and hands each
 * fired task off to a virtual thread.
 */
final class VirtualThreads {

  private static final Logger log = LoggerFactory.getLogger(VirtualThreads.class);

  /** The first Java version virtual threads are supported on. */
  static final int MINIMUM_SUPPORTED_JAVA_VERSION = 25;

  private static final MethodHandle NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR = lookupFactoryMethod();
  private static final AtomicBoolean UNSUPPORTED_WARNING_LOGGED = new AtomicBoolean();

  private VirtualThreads() {}

  private static MethodHandle lookupFactoryMethod() {
    try {
      return MethodHandles.publicLookup()
          .findStatic(
              Executors.class,
              "newVirtualThreadPerTaskExecutor",
              MethodType.methodType(ExecutorService.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      log.debug("Virtual threads are not available on this JVM", e);
      return null;
    }
  }

  /**
   * Whether the JVM the operator runs on supports virtual threads, i.e. is Java {@value
   * #MINIMUM_SUPPORTED_JAVA_VERSION} or later.
   */
  static boolean isSupported() {
    return isSupported(Runtime.version().feature());
  }

  static boolean isSupported(int javaFeatureVersion) {
    return NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR != null
        && javaFeatureVersion >= MINIMUM_SUPPORTED_JAVA_VERSION;
  }

  /**
   * Whether virtual threads should effectively be used, i.e. they were requested through {@link
   * ConfigurationService#useVirtualThreads()} <em>and</em> the JVM supports them. Requesting them
   * on a JVM that doesn't support them is only warned about, so that the same configuration can be
   * used regardless of the Java version the operator ends up running on, the only consequence being
   * that platform threads are used instead. Concurrency limits are enforced either way.
   */
  static boolean shouldUse(boolean requested) {
    if (!requested || isSupported()) {
      return requested;
    }
    if (UNSUPPORTED_WARNING_LOGGED.compareAndSet(false, true)) {
      log.warn(
          "Virtual threads were requested but are only supported on Java {} or later, and the JVM"
              + " in use is Java {}. Falling back to platform threads.",
          MINIMUM_SUPPORTED_JAVA_VERSION,
          Runtime.version().feature());
    }
    return false;
  }

  /** An unbounded executor starting a new virtual thread for each submitted task. */
  static ExecutorService newVirtualThreadPerTaskExecutor() {
    if (!isSupported()) {
      throw new OperatorException(
          "Virtual threads are only supported on Java "
              + MINIMUM_SUPPORTED_JAVA_VERSION
              + " or later");
    }
    try {
      return (ExecutorService) NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR.invokeExact();
    } catch (Throwable e) {
      throw new OperatorException("Couldn't create a virtual thread per task executor", e);
    }
  }

  /**
   * Supplies the task executor of the default {@link io.fabric8.kubernetes.client.KubernetesClient}
   * created by {@link ConfigurationService#getKubernetesClient()}: an unbounded virtual thread
   * executor, replacing the client's default cached platform thread pool, that is shut down when
   * the client is closed.
   */
  static ExecutorSupplier newKubernetesClientTaskExecutorSupplier() {
    return new ExecutorSupplier() {
      @Override
      public Executor get() {
        return newVirtualThreadPerTaskExecutor();
      }

      @Override
      public void onClose(Executor executor) {
        ((ExecutorService) executor).shutdownNow();
      }
    };
  }

  /**
   * A {@link ScheduledExecutorService} running the tasks it fires on virtual threads, without
   * limiting how many of them run at the same time. The timing is done by a single daemon platform
   * thread named after {@code threadNamePrefix}, which only hands the tasks off.
   */
  static ScheduledExecutorService newScheduledExecutor(String threadNamePrefix) {
    return new VirtualThreadScheduledExecutor(newVirtualThreadPerTaskExecutor(), threadNamePrefix);
  }

  /**
   * A virtual thread based executor executing at most {@code maxConcurrency} tasks at the same
   * time, the equivalent of a fixed size platform thread pool.
   */
  static ExecutorService newBoundedVirtualThreadExecutor(int maxConcurrency) {
    if (maxConcurrency <= 0) {
      throw new IllegalArgumentException("maxConcurrency must be positive: " + maxConcurrency);
    }
    return new BoundedExecutorService(newVirtualThreadPerTaskExecutor(), maxConcurrency);
  }

  /**
   * Limits how many of the tasks submitted to the wrapped executor run at the same time.
   *
   * <p>A thread is started for each task as soon as it is submitted, the task then waits for a
   * permit before it actually runs. This only makes sense with virtual threads, which are cheap
   * enough to be parked in large numbers, and has the property that submitting a task never blocks
   * the submitting thread, just like queuing it on a fixed size platform thread pool wouldn't.
   */
  private static final class BoundedExecutorService extends AbstractExecutorService {

    private final ExecutorService delegate;
    private final Semaphore permits;
    private final Set<PendingTask> pending = ConcurrentHashMap.newKeySet();
    private final Object lifecycleLock = new Object();

    private BoundedExecutorService(ExecutorService delegate, int maxConcurrency) {
      this.delegate = delegate;
      this.permits = new Semaphore(maxConcurrency, true);
    }

    @Override
    public void execute(Runnable command) {
      Objects.requireNonNull(command);
      var task = new PendingTask(command);
      // admission is closed under the same lock that shutdownNow drains pending tasks with, so a
      // task is either rejected or seen by the drain, and never interrupted while unaccounted for
      synchronized (lifecycleLock) {
        pending.add(task);
        try {
          delegate.execute(task);
        } catch (RuntimeException e) {
          pending.remove(task);
          throw e;
        }
      }
    }

    private final class PendingTask implements Runnable {

      private final Runnable command;
      private final AtomicBoolean claimed = new AtomicBoolean();

      private PendingTask(Runnable command) {
        this.command = command;
      }

      private boolean claim() {
        return claimed.compareAndSet(false, true);
      }

      @Override
      public void run() {
        try {
          permits.acquire();
        } catch (InterruptedException e) {
          // shutdownNow interrupted us and already handed the task back to its caller
          Thread.currentThread().interrupt();
          return;
        }
        try {
          if (!claim()) {
            return;
          }
          pending.remove(this);
          command.run();
        } finally {
          permits.release();
        }
      }
    }

    @Override
    public void shutdown() {
      delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
      var notStarted = new ArrayList<Runnable>();
      synchronized (lifecycleLock) {
        for (var task : pending) {
          if (task.claim()) {
            pending.remove(task);
            notStarted.add(task.command);
          }
        }
        delegate.shutdownNow();
      }
      return notStarted;
    }

    @Override
    public boolean isShutdown() {
      return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
      return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      return delegate.awaitTermination(timeout, unit);
    }
  }
}

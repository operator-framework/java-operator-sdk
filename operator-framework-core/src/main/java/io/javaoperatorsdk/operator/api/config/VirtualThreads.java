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
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * <p>The SDK is compiled for Java 17, in which virtual threads don't exist yet, so {@code
 * Executors.newVirtualThreadPerTaskExecutor()} is looked up reflectively and is only available when
 * the operator actually runs on Java 21 or later.
 *
 * <p>There is intentionally no scheduled variant here: the JDK provides no virtual thread backed
 * {@link java.util.concurrent.ScheduledExecutorService}, scheduling was deliberately left out of
 * virtual threads (the Loom runtime itself uses a platform thread scheduler to unpark timed out
 * virtual threads). Running scheduled tasks on virtual threads would mean keeping a platform thread
 * scheduler purely for the timing and handing each fired task off to a virtual thread executor,
 * which the SDK doesn't do, so {@link ExecutorServiceManager#scheduledExecutorService()} is always
 * backed by platform threads.
 */
final class VirtualThreads {

  private static final Logger log = LoggerFactory.getLogger(VirtualThreads.class);

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

  /** Whether the JVM the operator runs on supports virtual threads, i.e. is Java 21 or later. */
  static boolean isSupported() {
    return NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR != null;
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
          "Virtual threads were requested but are not supported by the JVM in use (Java {}, Java 21"
              + " or later is required, Java 25 or later is officially supported). Falling back to"
              + " platform threads.",
          Runtime.version().feature());
    }
    return false;
  }

  /** An unbounded executor starting a new virtual thread for each submitted task. */
  static ExecutorService newVirtualThreadPerTaskExecutor() {
    if (!isSupported()) {
      throw new OperatorException(
          "Virtual threads are not supported by the JVM in use, Java 21 or later is required"
              + " (Java 25 or later is officially supported)");
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
   * A virtual thread based executor executing at most {@code maxConcurrency} tasks at the same
   * time, the equivalent of a fixed size platform thread pool.
   */
  static ExecutorService newBoundedVirtualThreadExecutor(int maxConcurrency) {
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

    private BoundedExecutorService(ExecutorService delegate, int maxConcurrency) {
      this.delegate = delegate;
      this.permits = new Semaphore(maxConcurrency, true);
    }

    @Override
    public void execute(Runnable command) {
      delegate.execute(
          () -> {
            try {
              permits.acquire();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              // shutdownNow interrupted us before the task even started: cancel it so that whoever
              // waits on the associated future isn't left hanging
              if (command instanceof Future) {
                ((Future<?>) command).cancel(false);
              }
              return;
            }
            try {
              command.run();
            } finally {
              permits.release();
            }
          });
    }

    @Override
    public void shutdown() {
      delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
      return delegate.shutdownNow();
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

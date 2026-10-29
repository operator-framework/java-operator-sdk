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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.javaoperatorsdk.operator.ExecutorUtilsInternal;

/**
 * A {@link ScheduledExecutorService} running the tasks it fires on the threads of another executor,
 * which is how scheduled tasks end up on virtual threads: the JDK has no virtual thread backed
 * {@link ScheduledExecutorService}, so a single daemon platform thread does the timing and only
 * hands each fired task off.
 *
 * <p>The semantics follow the ones of the default scheduled executor of the SDK, see {@link
 * ExecutorUtilsInternal#daemonScheduledThreadPool(int, String)}: tasks that were scheduled for
 * later, and periodic tasks, are discarded when the executor is shut down, while tasks that already
 * started are let finish.
 */
final class VirtualThreadScheduledExecutor extends AbstractExecutorService
    implements ScheduledExecutorService {

  private final ExecutorService worker;
  private final ScheduledExecutorService timer;
  private final Set<Handle<?>> waiting = ConcurrentHashMap.newKeySet();
  private final Object lifecycleLock = new Object();

  VirtualThreadScheduledExecutor(ExecutorService worker, String threadNamePrefix) {
    this.worker = worker;
    this.timer = ExecutorUtilsInternal.daemonScheduledThreadPool(1, threadNamePrefix + "-timer");
  }

  @Override
  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    Objects.requireNonNull(command);
    return start(new Handle<Void>(Executors.callable(command, null), 0), delay, unit);
  }

  @Override
  public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
    Objects.requireNonNull(callable);
    return start(new Handle<>(callable, 0), delay, unit);
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(
      Runnable command, long initialDelay, long period, TimeUnit unit) {
    Objects.requireNonNull(command);
    if (period <= 0) {
      throw new IllegalArgumentException("period must be positive");
    }
    return start(
        new Handle<Void>(Executors.callable(command, null), unit.toNanos(period)),
        initialDelay,
        unit);
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(
      Runnable command, long initialDelay, long delay, TimeUnit unit) {
    Objects.requireNonNull(command);
    if (delay <= 0) {
      throw new IllegalArgumentException("delay must be positive");
    }
    return start(
        new Handle<Void>(Executors.callable(command, null), -unit.toNanos(delay)),
        initialDelay,
        unit);
  }

  private <V> Handle<V> start(Handle<V> handle, long initialDelay, TimeUnit unit) {
    handle.arm(System.nanoTime() + Math.max(0, unit.toNanos(initialDelay)));
    return handle;
  }

  @Override
  public void execute(Runnable command) {
    worker.execute(Objects.requireNonNull(command));
  }

  @Override
  public void shutdown() {
    synchronized (lifecycleLock) {
      cancelWaiting(false);
      timer.shutdown();
      worker.shutdown();
    }
  }

  @Override
  public List<Runnable> shutdownNow() {
    final var notStarted = new ArrayList<Runnable>();
    synchronized (lifecycleLock) {
      cancelWaiting(true);
      timer.shutdownNow();
      for (var task : worker.shutdownNow()) {
        if (task instanceof Dispatched dispatched) {
          dispatched.handle.cancel(false);
        } else {
          notStarted.add(task);
        }
      }
    }
    return Collections.unmodifiableList(notStarted);
  }

  private void cancelWaiting(boolean mayInterruptIfRunning) {
    for (var handle : waiting) {
      handle.cancel(mayInterruptIfRunning);
    }
  }

  @Override
  public boolean isShutdown() {
    return timer.isShutdown() && worker.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return timer.isTerminated() && worker.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    final var deadline = System.nanoTime() + unit.toNanos(timeout);
    return timer.awaitTermination(timeout, unit)
        && worker.awaitTermination(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
  }

  /**
   * What is handed to the worker once a task fires, so that it can be traced back to its handle.
   */
  private record Dispatched(Handle<?> handle) implements Runnable {
    @Override
    public void run() {
      handle.runOnWorker();
    }
  }

  /**
   * The future of a scheduled task. A positive {@code period} makes it a task at a fixed rate, a
   * negative one a task with a fixed delay, whose absolute value is the delay.
   */
  private final class Handle<V> implements ScheduledFuture<V> {

    private final Callable<V> callable;
    private final long period;
    private final CompletableFuture<V> result = new CompletableFuture<>();

    private volatile ScheduledFuture<?> timerFuture;
    private volatile Thread runner;
    private volatile long triggerTime;

    private Handle(Callable<V> callable, long period) {
      this.callable = callable;
      this.period = period;
    }

    private void arm(long triggerNanos) {
      triggerTime = triggerNanos;
      final var delayNanos = Math.max(0, triggerNanos - System.nanoTime());
      synchronized (lifecycleLock) {
        waiting.add(this);
        try {
          timerFuture = timer.schedule(this::dispatch, delayNanos, TimeUnit.NANOSECONDS);
        } catch (RejectedExecutionException e) {
          waiting.remove(this);
          throw e;
        }
      }
      if (result.isDone()) {
        waiting.remove(this);
        timerFuture.cancel(false);
      }
    }

    private void dispatch() {
      waiting.remove(this);
      if (result.isDone()) {
        return;
      }
      try {
        worker.execute(new Dispatched(this));
      } catch (RejectedExecutionException e) {
        result.cancel(false);
      }
    }

    private void runOnWorker() {
      runner = Thread.currentThread();
      try {
        if (result.isDone()) {
          return;
        }
        if (period == 0) {
          result.complete(callable.call());
          return;
        }
        callable.call();
      } catch (Throwable t) {
        result.completeExceptionally(t);
        return;
      } finally {
        runner = null;
      }
      rearm();
    }

    private void rearm() {
      if (result.isDone()) {
        return;
      }
      final long nextTrigger;
      if (period > 0) {
        nextTrigger = triggerTime + period;
      } else {
        nextTrigger = System.nanoTime() - period;
      }
      try {
        arm(nextTrigger);
      } catch (RejectedExecutionException e) {
        result.cancel(false);
      }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      final var cancelled = result.cancel(mayInterruptIfRunning);
      if (cancelled) {
        waiting.remove(this);
        final var future = timerFuture;
        if (future != null) {
          future.cancel(false);
        }
        final var thread = runner;
        if (mayInterruptIfRunning && thread != null) {
          thread.interrupt();
        }
      }
      return cancelled;
    }

    @Override
    public boolean isCancelled() {
      return result.isCancelled();
    }

    @Override
    public boolean isDone() {
      return result.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
      return result.get();
    }

    @Override
    public V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return result.get(timeout, unit);
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return unit.convert(triggerTime - System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
      if (other == this) {
        return 0;
      }
      return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
    }
  }
}

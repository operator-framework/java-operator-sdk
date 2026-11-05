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
package io.javaoperatorsdk.operator;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class ExecutorUtilsInternal {

  private static final long SCHEDULED_THREAD_KEEP_ALIVE_SECONDS = 60;

  // prevent instantiation of util class
  private ExecutorUtilsInternal() {}

  /**
   * Creates a {@link ThreadFactory} producing daemon threads named after the specified prefix.
   * Daemon threads don't keep the JVM alive if the {@link Operator} is never stopped, and naming
   * them makes the pool they belong to identifiable in thread dumps.
   *
   * @param namePrefix the prefix the created threads are named after
   * @return a {@link ThreadFactory} creating named daemon threads
   */
  public static ThreadFactory daemonThreadFactory(String namePrefix) {
    final var defaultThreadFactory = Executors.defaultThreadFactory();
    final var counter = new AtomicLong();
    return runnable -> {
      final var thread = defaultThreadFactory.newThread(runnable);
      thread.setName(namePrefix + "-" + counter.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    };
  }

  /**
   * Creates the kind of {@link ScheduledExecutorService} the operator runs its scheduled (i.e.
   * periodic or delayed) tasks on: one whose threads are daemon threads named after the specified
   * prefix, and which doesn't let the tasks that were scheduled for later delay its shutdown.
   *
   * @param corePoolSize the maximal number of threads of the pool, which are created on demand and
   *     let go if idle for a while
   * @param threadNamePrefix the prefix the threads of the pool are named after
   * @return a {@link ScheduledExecutorService} to run scheduled tasks on
   */
  public static ScheduledExecutorService daemonScheduledThreadPool(
      int corePoolSize, String threadNamePrefix) {
    final var executor =
        new ScheduledThreadPoolExecutor(corePoolSize, daemonThreadFactory(threadNamePrefix));
    // tasks that are scheduled far out (a reconciliation rescheduled in an hour, say) would
    // otherwise keep the pool from terminating until the graceful shutdown timeout expires
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    // cancelled tasks are frequent (every retry that is superseded by a new event cancels one) and
    // would otherwise be retained until their delay elapses
    executor.setRemoveOnCancelPolicy(true);
    // the threads are created as tasks get scheduled, not as the load requires, so without this
    // they would all stay around idle for good; the pool always keeps a thread for delayed tasks
    executor.setKeepAliveTime(SCHEDULED_THREAD_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS);
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }
}

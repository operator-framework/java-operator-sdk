package io.javaoperatorsdk.operator.api.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

class ExecutorServiceProducer {

  private final static AtomicReference<ScheduledThreadPoolExecutor> executor =
      new AtomicReference<>();

  static ExecutorService getExecutor(int threadPoolSize) {
    final var gotSet =
        executor.compareAndSet(null, new ScheduledThreadPoolExecutor(threadPoolSize));
    final var result = executor.get();
    if (!gotSet) {
      // check that we didn't try to change the pool size
      if (result.getCorePoolSize() != threadPoolSize) {
        throw new IllegalArgumentException(
            "Cannot change the ExecutorService's thread pool size once set! Was "
                + result.getCorePoolSize() + ", attempted to retrieve " + threadPoolSize);
      }
    }
    return result;
  }
}

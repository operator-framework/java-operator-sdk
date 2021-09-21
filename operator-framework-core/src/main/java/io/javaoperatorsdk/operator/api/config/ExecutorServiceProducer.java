package io.javaoperatorsdk.operator.api.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class ExecutorServiceProducer {

  private final static AtomicReference<ThreadPoolExecutor> executor =
      new AtomicReference<>();

  static ExecutorService getExecutor(int threadPoolSize) {
    final var gotSet =
        executor.compareAndSet(null, new InstrumentedExecutorService(threadPoolSize));
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

  private static class InstrumentedExecutorService extends ThreadPoolExecutor {
    private final boolean debug;

    public InstrumentedExecutorService(int corePoolSize) {
      super(corePoolSize, corePoolSize, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
      debug = Utils.debugThreadPool();
    }

    @Override
    public void shutdown() {
      if (debug) {
        Thread.dumpStack();
      }
      super.shutdown();
    }
  }
}

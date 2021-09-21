package io.javaoperatorsdk.operator.api.config;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class ExecutorServiceProducer {

  private final static AtomicReference<ThreadPoolExecutor> executor =
      new AtomicReference<>();

  static ExecutorService getExecutor(int threadPoolSize) {
    final var gotSet =
        executor.compareAndSet(null, new DebugThreadPoolExecutor(threadPoolSize, threadPoolSize, 0L,
            TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()));
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

  private static class DebugThreadPoolExecutor extends ThreadPoolExecutor {

    public DebugThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
        TimeUnit unit,
        BlockingQueue<Runnable> workQueue) {
      super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public DebugThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
        TimeUnit unit, BlockingQueue<Runnable> workQueue,
        ThreadFactory threadFactory) {
      super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public DebugThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
        TimeUnit unit, BlockingQueue<Runnable> workQueue,
        RejectedExecutionHandler handler) {
      super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public DebugThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
        TimeUnit unit, BlockingQueue<Runnable> workQueue,
        ThreadFactory threadFactory, RejectedExecutionHandler handler) {
      super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    @Override
    public void shutdown() {
      Thread.dumpStack();
      super.shutdown();
    }
  }
}

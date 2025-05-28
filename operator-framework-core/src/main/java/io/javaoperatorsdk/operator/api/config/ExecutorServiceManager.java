package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.OperatorException;

public class ExecutorServiceManager {

  private static final Logger log = LoggerFactory.getLogger(ExecutorServiceManager.class);
  private ExecutorService executor;
  private ExecutorService workflowExecutor;
  private ExecutorService cachingExecutorService;
  private boolean started;
  private ConfigurationService configurationService;

  ExecutorServiceManager(ConfigurationService configurationService) {
    start(configurationService);
  }

  /**
   * Uses cachingExecutorService from this manager. Use this only for tasks, that don't have dynamic
   * nature, in sense that won't grow with the number of inputs (thus kubernetes resources)
   *
   * @param stream of elements
   * @param task to call on stream elements
   * @param threadNamer for naming thread
   * @param <T> type
   */
  public <T> void boundedExecuteAndWaitForAllToComplete(
      Stream<T> stream, Function<T, Void> task, Function<T, String> threadNamer) {
    executeAndWaitForAllToComplete(stream, task, threadNamer, cachingExecutorService());
  }

  public static <T> void executeAndWaitForAllToComplete(
      Stream<T> stream,
      Function<T, Void> task,
      Function<T, String> threadNamer,
      ExecutorService executorService) {
    final var instrumented = new InstrumentedExecutorService(executorService);
    try {
      instrumented
          .invokeAll(
              stream
                  .map(
                      item ->
                          (Callable<Void>)
                              () -> {
                                // change thread name for easier debugging
                                final var thread = Thread.currentThread();
                                final var name = thread.getName();
                                thread.setName(threadNamer.apply(item));
                                try {
                                  task.apply(item);
                                  return null;
                                } finally {
                                  // restore original name
                                  thread.setName(name);
                                }
                              })
                  .collect(Collectors.toList()))
          .forEach(
              f -> {
                try {
                  // to find out any exceptions
                  f.get();
                } catch (ExecutionException e) {
                  throw new OperatorException(e);
                } catch (InterruptedException e) {
                  log.warn("Interrupted.", e);
                  Thread.currentThread().interrupt();
                }
              });
    } catch (InterruptedException e) {
      log.warn("Interrupted.", e);
      Thread.currentThread().interrupt();
    }
  }

  public ExecutorService reconcileExecutorService() {
    return executor;
  }

  public ExecutorService workflowExecutorService() {
    lazyInitWorkflowExecutorService();
    return workflowExecutor;
  }

  private synchronized void lazyInitWorkflowExecutorService() {
    if (workflowExecutor == null) {
      workflowExecutor =
          new InstrumentedExecutorService(configurationService.getWorkflowExecutorService());
    }
  }

  public ExecutorService cachingExecutorService() {
    return cachingExecutorService;
  }

  public void start(ConfigurationService configurationService) {
    if (!started) {
      this.configurationService = configurationService; // used to lazy init workflow executor
      this.cachingExecutorService = Executors.newCachedThreadPool();
      this.executor = new InstrumentedExecutorService(configurationService.getExecutorService());
      started = true;
    }
  }

  public void stop(Duration gracefulShutdownTimeout) {
    try {
      log.debug("Closing executor");
      var parallelExec = Executors.newFixedThreadPool(3);
      parallelExec.invokeAll(
          List.of(
              shutdown(executor, gracefulShutdownTimeout),
              shutdown(workflowExecutor, gracefulShutdownTimeout),
              shutdown(cachingExecutorService, gracefulShutdownTimeout)));
      workflowExecutor = null;
      parallelExec.shutdownNow();
      started = false;
    } catch (InterruptedException e) {
      log.debug("Exception closing executor: {}", e.getLocalizedMessage());
      Thread.currentThread().interrupt();
    }
  }

  private static Callable<Void> shutdown(
      ExecutorService executorService, Duration gracefulShutdownTimeout) {
    return () -> {
      // workflow executor can be null
      if (executorService == null) {
        return null;
      }
      executorService.shutdown();
      if (!executorService.awaitTermination(
          gracefulShutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
        executorService.shutdownNow(); // if we timed out, waiting, cancel everything
      }
      return null;
    };
  }

  private static class InstrumentedExecutorService implements ExecutorService {
    private final boolean debug;
    private final ExecutorService executor;

    private InstrumentedExecutorService(ExecutorService executor) {
      if (executor == null) {
        throw new NullPointerException();
      }
      this.executor = executor;
      debug = Utils.debugThreadPool();
    }

    @Override
    public void shutdown() {
      if (debug) {
        Thread.dumpStack();
      }
      executor.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
      return executor.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
      return executor.isShutdown();
    }

    @Override
    public boolean isTerminated() {
      return executor.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      return executor.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
      return executor.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
      return executor.submit(task, result);
    }

    @Override
    public Future<?> submit(Runnable task) {
      return executor.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException {
      return executor.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(
        Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException {
      return executor.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException {
      return executor.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return executor.invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
      executor.execute(command);
    }
  }
}

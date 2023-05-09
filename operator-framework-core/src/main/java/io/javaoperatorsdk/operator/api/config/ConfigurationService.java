package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceFactory;
import io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflowFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static io.javaoperatorsdk.operator.api.config.ExecutorServiceManager.newThreadPoolExecutor;

/** An interface from which to retrieve configuration information. */
public interface ConfigurationService {

  Logger log = LoggerFactory.getLogger(ConfigurationService.class);

  /**
   * Retrieves the configuration associated with the specified reconciler
   *
   * @param reconciler the reconciler we want the configuration of
   * @param <R> the {@code CustomResource} type associated with the specified reconciler
   * @return the {@link ControllerConfiguration} associated with the specified reconciler or {@code
   * null} if no configuration exists for the reconciler
   */
  <R extends HasMetadata> ControllerConfiguration<R> getConfigurationFor(Reconciler<R> reconciler);

  /**
   * Retrieves the Kubernetes client configuration
   *
   * @return the configuration of the Kubernetes client, defaulting to the provided
   *         auto-configuration
   */
  default Config getClientConfiguration() {
    return Config.autoConfigure(null);
  }

  /**
   * Retrieves the set of the names of reconcilers for which a configuration exists
   *
   * @return the set of known reconciler names
   */
  Set<String> getKnownReconcilerNames();

  /**
   * Retrieves the {@link Version} information associated with this particular instance of the SDK
   *
   * @return the version information
   */
  Version getVersion();

  /**
   * Whether the operator should query the CRD to make sure it's deployed and validate
   * {@link CustomResource} implementations before attempting to register the associated
   * reconcilers.
   *
   * <p>
   * Note that this might require elevating the privileges associated with the operator to gain read
   * access on the CRD resources.
   *
   * @return {@code true} if CRDs should be checked (default), {@code false} otherwise
   */
  default boolean checkCRDAndValidateLocalModel() {
    return false;
  }

  int DEFAULT_RECONCILIATION_THREADS_NUMBER = 200;
  int MIN_DEFAULT_RECONCILIATION_THREADS_NUMBER = 10;

  /**
   * The maximum number of threads the operator can spin out to dispatch reconciliation requests to
   * reconcilers
   *
   * @return the maximum number of concurrent reconciliation threads
   */
  default int concurrentReconciliationThreads() {
    return DEFAULT_RECONCILIATION_THREADS_NUMBER;
  }

  /**
   * The minimum number of threads the operator starts in the thread pool for reconciliations.
   *
   * @return the minimum number of concurrent reconciliation threads
   */
  default int minConcurrentReconciliationThreads() {
    return MIN_DEFAULT_RECONCILIATION_THREADS_NUMBER;
  }

  int DEFAULT_WORKFLOW_EXECUTOR_THREAD_NUMBER = DEFAULT_RECONCILIATION_THREADS_NUMBER;
  int MIN_DEFAULT_WORKFLOW_EXECUTOR_THREAD_NUMBER = MIN_DEFAULT_RECONCILIATION_THREADS_NUMBER;

  /**
   * Retrieves the maximum number of threads the operator can spin out to be used in the workflows.
   *
   * @return the maximum number of concurrent workflow threads
   */
  default int concurrentWorkflowExecutorThreads() {
    return DEFAULT_WORKFLOW_EXECUTOR_THREAD_NUMBER;
  }

  /**
   * The minimum number of threads the operator starts in the thread pool for workflows.
   *
   * @return the minimum number of concurrent workflow threads
   */
  default int minConcurrentWorkflowExecutorThreads() {
    return MIN_DEFAULT_WORKFLOW_EXECUTOR_THREAD_NUMBER;
  }

  /**
   * Used to clone custom resources. It is strongly suggested that implementors override this method
   * since the default implementation creates a new {@link Cloner} instance each time this method is
   * called.
   *
   * @return the configured {@link Cloner}
   */
  default Cloner getResourceCloner() {
    return new Cloner() {
      @SuppressWarnings("unchecked")
      @Override
      public HasMetadata clone(HasMetadata object) {
        try {
          final var mapper = getObjectMapper();
          return mapper.readValue(mapper.writeValueAsString(object), object.getClass());
        } catch (JsonProcessingException e) {
          throw new IllegalStateException(e);
        }
      }
    };
  }

  int DEFAULT_TERMINATION_TIMEOUT_SECONDS = 10;

  /**
   * Retrieves the number of seconds the SDK waits for reconciliation threads to terminate before
   * shutting down.
   *
   * @deprecated use {@link io.javaoperatorsdk.operator.Operator#stop(Duration)} instead. Where the
   *             parameter can be passed to specify graceful timeout.
   *
   * @return the number of seconds to wait before terminating reconciliation threads
   */
  @Deprecated(forRemoval = true)
  default int getTerminationTimeoutSeconds() {
    return DEFAULT_TERMINATION_TIMEOUT_SECONDS;
  }

  default Metrics getMetrics() {
    return Metrics.NOOP;
  }

  default ExecutorService getExecutorService() {
    return newThreadPoolExecutor(minConcurrentReconciliationThreads(),
        concurrentReconciliationThreads());
  }

  default ExecutorService getWorkflowExecutorService() {
    return newThreadPoolExecutor(minConcurrentWorkflowExecutorThreads(),
        concurrentWorkflowExecutorThreads());
  }

  default boolean closeClientOnStop() {
    return true;
  }

  default ObjectMapper getObjectMapper() {
    return Serialization.jsonMapper();
  }

  @SuppressWarnings("rawtypes")
  default DependentResourceFactory dependentResourceFactory() {
    return DependentResourceFactory.DEFAULT;
  }

  default Optional<LeaderElectionConfiguration> getLeaderElectionConfiguration() {
    return Optional.empty();
  }

  /**
   * <p>
   * if true, operator stops if there are some issues with informers
   * {@link io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource} or
   * {@link io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource}
   * on startup. Other event sources may also respect this flag.
   * </p>
   * <p>
   * if false, the startup will ignore recoverable errors, caused for example by RBAC issues, and
   * will try to reconnect periodically in the background.
   * </p>
   *
   * @return actual value described above
   */
  default boolean stopOnInformerErrorDuringStartup() {
    return true;
  }

  /**
   * Timeout for cache sync. In other words source start timeout. Note that is
   * "stopOnInformerErrorDuringStartup" is true the operator will stop on timeout. Default is 2
   * minutes.
   *
   * @return Duration of sync timeout
   */
  default Duration cacheSyncTimeout() {
    return Duration.ofMinutes(2);
  }

  /**
   * Handler for an informer stop. Informer stops if there is a non-recoverable error. Like received
   * a resource that cannot be deserialized.
   *
   * @return an optional InformerStopHandler
   */
  default Optional<InformerStoppedHandler> getInformerStoppedHandler() {
    return Optional.of((informer, ex) -> {
      // hasSynced is checked to verify that informer already started. If not started, in case
      // of a fatal error the operator will stop, no need for explicit exit.
      if (ex != null && informer.hasSynced()) {
        log.error("Fatal error in informer: {}. Stopping the operator", informer, ex);
        System.exit(1);
      } else {
        log.debug(
            "Informer stopped: {}. Has synced: {}, Error: {}. This can happen as a result of " +
                "stopping the controller, or due to an error on startup." +
                "See also stopOnInformerErrorDuringStartup configuration.",
            informer, informer.hasSynced(), ex);
      }
    });
  }

  @SuppressWarnings("rawtypes")
  default ManagedWorkflowFactory getWorkflowFactory() {
    return ManagedWorkflowFactory.DEFAULT;
  }

  default ResourceClassResolver getResourceClassResolver() {
    return new DefaultResourceClassResolver();
  }

  static ConfigurationService newOverriddenConfigurationService(
      ConfigurationService baseConfiguration,
      Consumer<ConfigurationServiceOverrider> overrider) {
    if (overrider != null) {
      final var toOverride = new ConfigurationServiceOverrider(baseConfiguration);
      overrider.accept(toOverride);
      return toOverride.build();
    }
    return baseConfiguration;
  }

  default ExecutorServiceManager getExecutorServiceManager() {
    return new ExecutorServiceManager(this);
  }
}

package io.javaoperatorsdk.operator.api.config;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** An interface from which to retrieve configuration information. */
public interface ConfigurationService {

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

  int DEFAULT_RECONCILIATION_THREADS_NUMBER = 10;

  /**
   * Retrieves the maximum number of threads the operator can spin out to dispatch reconciliation
   * requests to reconcilers
   *
   * @return the maximum number of concurrent reconciliation threads
   */
  default int concurrentReconciliationThreads() {
    return DEFAULT_RECONCILIATION_THREADS_NUMBER;
  }

  int DEFAULT_WORKFLOW_EXECUTOR_THREAD_NUMBER = DEFAULT_RECONCILIATION_THREADS_NUMBER;

  default int concurrentWorkflowExecutorThreads() {
    return DEFAULT_WORKFLOW_EXECUTOR_THREAD_NUMBER;
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
   * @return the number of seconds to wait before terminating reconciliation threads
   */
  default int getTerminationTimeoutSeconds() {
    return DEFAULT_TERMINATION_TIMEOUT_SECONDS;
  }

  default Metrics getMetrics() {
    return Metrics.NOOP;
  }

  default ExecutorService getExecutorService() {
    return Executors.newFixedThreadPool(concurrentReconciliationThreads());
  }

  default ExecutorService getWorkflowExecutorService() {
    return Executors.newFixedThreadPool(concurrentWorkflowExecutorThreads());
  }

  default boolean closeClientOnStop() {
    return true;
  }

  default ObjectMapper getObjectMapper() {
    return Serialization.jsonMapper();
  }

  default DependentResourceFactory dependentResourceFactory() {
    return new DependentResourceFactory() {};
  }

  default Optional<LeaderElectionConfiguration> getLeaderElectionConfiguration() {
    return Optional.empty();
  }

}

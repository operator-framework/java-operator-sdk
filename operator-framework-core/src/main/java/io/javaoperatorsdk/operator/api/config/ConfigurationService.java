package io.javaoperatorsdk.operator.api.config;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** An interface from which to retrieve configuration information. */
public interface ConfigurationService {

  ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  Cloner DEFAULT_CLONER = new Cloner() {
    @Override
    public HasMetadata clone(HasMetadata object) {
      try {
        return OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(object), object.getClass());
      } catch (JsonProcessingException e) {
        throw new IllegalStateException(e);
      }
    }
  };

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
    return true;
  }

  int DEFAULT_RECONCILIATION_THREADS_NUMBER = 5;

  /**
   * Retrieves the maximum number of threads the operator can spin out to dispatch reconciliation
   * requests to reconcilers
   *
   * @return the maximum number of concurrent reconciliation threads
   */
  default int concurrentReconciliationThreads() {
    return DEFAULT_RECONCILIATION_THREADS_NUMBER;
  }

  /**
   * Used to clone custom resources.
   *
   * @return the ObjectMapper to use
   */
  default Cloner getResourceCloner() {
    return DEFAULT_CLONER;
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

  default boolean closeClientOnStop() {
    return true;
  }

  default ObjectMapper getObjectMapper() {
    return OBJECT_MAPPER;
  }
}

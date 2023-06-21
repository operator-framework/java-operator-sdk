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
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceFactory;
import io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflowFactory;

import static io.javaoperatorsdk.operator.api.config.ExecutorServiceManager.newThreadPoolExecutor;

/** An interface from which to retrieve configuration information. */
public interface ConfigurationService {

  Logger log = LoggerFactory.getLogger(ConfigurationService.class);

  int DEFAULT_MAX_CONCURRENT_REQUEST = 512;

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
   * Used to clone custom resources.
   *
   * <p>
   * <em>NOTE:</em> It is strongly suggested that implementors override this method since the
   * default implementation creates a new {@link Cloner} instance each time this method is called.
   * </p>
   *
   * @return the configured {@link Cloner}
   */
  default Cloner getResourceCloner() {
    return new Cloner() {
      @Override
      public <R extends HasMetadata> R clone(R object) {
        return getKubernetesClient().getKubernetesSerialization().clone(object);
      }
    };
  }

  /**
   * Provides the fully configured {@link KubernetesClient} to use for controllers to the target
   * cluster. Note that this client only needs to be able to connect to the cluster, the SDK will
   * take care of creating the required connections to watch the target resources (in particular,
   * you do not need to worry about setting the namespace information in most cases).
   *
   * <p>
   * Previous versions of this class provided direct access to the serialization mechanism (via
   * {@link com.fasterxml.jackson.databind.ObjectMapper}) or the client's configuration. This was
   * somewhat confusing, in particular with respect to changes made in the Fabric8 client
   * serialization architecture made in 6.7. The proper way to configure these aspects is now to
   * configure the Kubernetes client accordingly and the SDK will extract the information it needs
   * from this instance. The recommended way to do so is to create your operator with
   * {@link io.javaoperatorsdk.operator.Operator#Operator(Consumer)}, passing your custom instance
   * with {@link ConfigurationServiceOverrider#withKubernetesClient(KubernetesClient)}.
   * </p>
   *
   * <p>
   * <em>NOTE:</em> It is strongly suggested that implementors override this method since the
   * default implementation creates a new {@link KubernetesClient} instance each time this method is
   * called.
   * </p>
   *
   * @return the configured {@link KubernetesClient}
   * @since 4.4.0
   */
  default KubernetesClient getKubernetesClient() {
    return new KubernetesClientBuilder()
        .withConfig(new ConfigBuilder(Config.autoConfigure(null))
            .withMaxConcurrentRequests(DEFAULT_MAX_CONCURRENT_REQUEST)
            .build())
        .withKubernetesSerialization(new KubernetesSerialization())
        .build();
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

  /**
   * Creates a new {@link ConfigurationService} instance used to configure an
   * {@link io.javaoperatorsdk.operator.Operator} instance, starting from the specified base
   * configuration and overriding specific aspects according to the provided
   * {@link ConfigurationServiceOverrider} instance.
   *
   * <p>
   * <em>NOTE:</em> This overriding mechanism should only be used <strong>before</strong> creating
   * your Operator instance as the configuration service is set at creation time and cannot be
   * subsequently changed. As a result, overriding values this way after the Operator has been
   * configured will not take effect.
   * </p>
   *
   * @param baseConfiguration the {@link ConfigurationService} to start from
   * @param overrider the {@link ConfigurationServiceOverrider} used to change the values provided
   *        by the base configuration
   * @return a new {@link ConfigurationService} starting from the configuration provided as base but
   *         with overridden values.
   */
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

  /**
   * Creates a new {@link ConfigurationService} instance used to configure an
   * {@link io.javaoperatorsdk.operator.Operator} instance, starting from the default configuration
   * and overriding specific aspects according to the provided {@link ConfigurationServiceOverrider}
   * instance.
   *
   * <p>
   * <em>NOTE:</em> This overriding mechanism should only be used <strong>before</strong> creating
   * your Operator instance as the configuration service is set at creation time and cannot be
   * subsequently changed. As a result, overriding values this way after the Operator has been
   * configured will not take effect.
   * </p>
   *
   * @param overrider the {@link ConfigurationServiceOverrider} used to change the values provided
   *        by the default configuration
   * @return a new {@link ConfigurationService} overriding the default values with the ones provided
   *         by the specified {@link ConfigurationServiceOverrider}
   * @since 4.4.0
   */
  static ConfigurationService newOverriddenConfigurationService(
      Consumer<ConfigurationServiceOverrider> overrider) {
    return newOverriddenConfigurationService(new BaseConfigurationService(), overrider);
  }

  default ExecutorServiceManager getExecutorServiceManager() {
    return new ExecutorServiceManager(this);
  }

  /**
   * Allows to revert to the 4.3 behavior when it comes to creating or updating Kubernetes Dependent
   * Resources when set to {@code false}. The default approach how these resources are
   * created/updated was change to use
   * <a href="https://kubernetes.io/docs/reference/using-api/server-side-apply/">Server-Side
   * Apply</a> (SSA) by default. Note that the legacy approach, and this setting, might be removed
   * in the future.
   *
   * @since 4.4.0
   */
  default boolean ssaBasedCreateUpdateForDependentResources() {
    return true;
  }

  /**
   * Allows to revert to the 4.3 generic matching algorithm for Kubernetes Dependent Resources when
   * set to {@code false}. Version 4.4 introduced a new generic matching algorithm for Kubernetes
   * Dependent Resources which is quite complex. As a consequence, we introduced this setting to
   * allow folks to revert to the previous matching algorithm if needed. Note, however, that the
   * legacy algorithm, and this setting, might be removed in the future.
   *
   * @since 4.4.0
   */
  default boolean ssaBasedDefaultMatchingForDependentResources() {
    return true;
  }

}

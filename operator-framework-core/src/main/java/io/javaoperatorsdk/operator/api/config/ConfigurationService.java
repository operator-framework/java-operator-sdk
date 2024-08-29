package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceFactory;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.ResourceUpdaterMatcher;
import io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflowFactory;

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

  int DEFAULT_RECONCILIATION_THREADS_NUMBER = 50;
  /**
   * @deprecated Not used anymore in the default implementation
   */
  @Deprecated(forRemoval = true)
  int MIN_DEFAULT_RECONCILIATION_THREADS_NUMBER = 10;

  /**
   * The number of threads the operator can spin out to dispatch reconciliation requests to
   * reconcilers with the default executors
   *
   * @return the number of concurrent reconciliation threads
   */
  default int concurrentReconciliationThreads() {
    return DEFAULT_RECONCILIATION_THREADS_NUMBER;
  }

  /**
   * The minimum number of threads the operator starts in the thread pool for reconciliations.
   *
   * @deprecated not used anymore by default executor implementation
   * @return the minimum number of concurrent reconciliation threads
   */
  @Deprecated(forRemoval = true)
  default int minConcurrentReconciliationThreads() {
    return MIN_DEFAULT_RECONCILIATION_THREADS_NUMBER;
  }

  int DEFAULT_WORKFLOW_EXECUTOR_THREAD_NUMBER = DEFAULT_RECONCILIATION_THREADS_NUMBER;
  /**
   * @deprecated Not used anymore in the default implementation
   */
  @Deprecated(forRemoval = true)
  int MIN_DEFAULT_WORKFLOW_EXECUTOR_THREAD_NUMBER = MIN_DEFAULT_RECONCILIATION_THREADS_NUMBER;

  /**
   * Number of threads the operator can spin out to be used in the workflows with the default
   * executor.
   *
   * @return the maximum number of concurrent workflow threads
   */
  default int concurrentWorkflowExecutorThreads() {
    return DEFAULT_WORKFLOW_EXECUTOR_THREAD_NUMBER;
  }

  /**
   * The minimum number of threads the operator starts in the thread pool for workflows.
   *
   * @deprecated not used anymore by default executor implementation
   * @return the minimum number of concurrent workflow threads
   */
  @Deprecated(forRemoval = true)
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
    return Executors.newFixedThreadPool(concurrentReconciliationThreads());
  }

  default ExecutorService getWorkflowExecutorService() {
    return Executors.newFixedThreadPool(concurrentWorkflowExecutorThreads());
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
   * Allows to revert to the 4.3 behavior when it comes to creating, updating and matching
   * Kubernetes Dependent Resources when set to {@code false}. The default approach how these
   * resources are created/updated and match was change to use
   * <a href="https://kubernetes.io/docs/reference/using-api/server-side-apply/">Server-Side
   * Apply</a> (SSA) by default.
   * <p>
   * SSA based create/update can be still used with the legacy matching, just overriding the match
   * method of Kubernetes Dependent Resource.
   *
   * @since 4.4.0
   */
  default boolean ssaBasedCreateUpdateMatchForDependentResources() {
    return true;
  }

  /**
   * This is mostly useful as an integration point for downstream projects to be able to reuse the
   * logic used to determine whether a given {@link KubernetesDependentResource} should use SSA or
   * not.
   *
   * @param dependentResource the {@link KubernetesDependentResource} under consideration
   * @return {@code true} if SSA should be used for
   * @param <R> the dependent resource type
   * @param <P> the primary resource type
   * @since 4.9.4
   */
  default <R extends HasMetadata, P extends HasMetadata> boolean shouldUseSSA(
      KubernetesDependentResource<R, P> dependentResource) {
    if (dependentResource instanceof ResourceUpdaterMatcher) {
      return false;
    }
    Optional<Boolean> useSSAConfig = dependentResource.configuration()
        .flatMap(KubernetesDependentResourceConfig::useSSA);
    // don't use SSA for certain resources by default, only if explicitly overriden
    if (useSSAConfig.isEmpty()
        && defaultNonSSAResource().contains(dependentResource.resourceType())) {
      return false;
    }
    return useSSAConfig.orElse(ssaBasedCreateUpdateMatchForDependentResources());
  }

  /**
   * Returns the set of default resources for which Server-Side Apply (SSA) will not be used, even
   * if it is the default behavior for dependent resources as specified by
   * {@link #ssaBasedCreateUpdateMatchForDependentResources()}. The exception to this is in the case
   * where the use of SSA is explicitly enabled on the dependent resource directly using
   * {@link KubernetesDependent#useSSA()}.
   * <p>
   * By default, SSA is disabled for {@link ConfigMap} and {@link Secret} resources.
   *
   * @return The set of resource types for which SSA will not be used
   * @since 4.4.0
   */
  default Set<Class<? extends HasMetadata>> defaultNonSSAResource() {
    return Set.of(ConfigMap.class, Secret.class);
  }

  /**
   * If a javaoperatorsdk.io/previous annotation should be used so that the operator sdk can detect
   * events from its own updates of dependent resources and then filter them.
   * <p>
   * Disable this if you want to react to your own dependent resource updates
   *
   * @since 4.5.0
   */
  default boolean previousAnnotationForDependentResourcesEventFiltering() {
    return true;
  }

  /**
   * If the event logic should parse the resourceVersion to determine the ordering of dependent
   * resource events. This is typically not needed.
   * <p>
   * Disabled by default as Kubernetes does not support, and discourages, this interpretation of
   * resourceVersions. Enable only if your api server event processing seems to lag the operator
   * logic and you want to further minimize the amount of work done / updates issued by the
   * operator.
   *
   * @since 4.5.0
   */
  default boolean parseResourceVersionsForEventFilteringAndCaching() {
    return false;
  }

  /**
   * {@link io.javaoperatorsdk.operator.api.reconciler.UpdateControl} patchStatus can either use
   * simple update or SSA for status subresource patching.
   *
   * @return true by default
   */
  default boolean useSSAForResourceStatusPatch() {
    return true;
  }

}

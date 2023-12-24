package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceFactory;

@SuppressWarnings("unused")
public class ConfigurationServiceOverrider {

  private static final Logger log = LoggerFactory.getLogger(ConfigurationServiceOverrider.class);
  private final ConfigurationService original;
  private Metrics metrics;
  private Boolean checkCR;
  private Integer concurrentReconciliationThreads;
  private Integer minConcurrentReconciliationThreads;
  private Integer concurrentWorkflowExecutorThreads;
  private Integer minConcurrentWorkflowExecutorThreads;
  private Cloner cloner;
  private Integer timeoutSeconds;
  private Boolean closeClientOnStop;
  private KubernetesClient client;
  private ExecutorService executorService;
  private ExecutorService workflowExecutorService;
  private LeaderElectionConfiguration leaderElectionConfiguration;
  private InformerStoppedHandler informerStoppedHandler;
  private Boolean stopOnInformerErrorDuringStartup;
  private Duration cacheSyncTimeout;
  private ResourceClassResolver resourceClassResolver;
  private Boolean ssaBasedCreateUpdateMatchForDependentResources;
  private Set<Class<? extends HasMetadata>> defaultNonSSAResource;
  private Boolean previousAnnotationForDependentResources;
  private Boolean parseResourceVersions;
  private DependentResourceFactory dependentResourceFactory;

  ConfigurationServiceOverrider(ConfigurationService original) {
    this.original = original;
  }

  public ConfigurationServiceOverrider checkingCRDAndValidateLocalModel(boolean check) {
    this.checkCR = check;
    return this;
  }

  public ConfigurationServiceOverrider withConcurrentReconciliationThreads(int threadNumber) {
    this.concurrentReconciliationThreads = threadNumber;
    return this;
  }

  public ConfigurationServiceOverrider withConcurrentWorkflowExecutorThreads(int threadNumber) {
    this.concurrentWorkflowExecutorThreads = threadNumber;
    return this;
  }

  private int minimumMaxValueFor(Integer minValue) {
    return minValue != null ? (minValue < 0 ? 0 : minValue) + 1 : 1;
  }

  public ConfigurationServiceOverrider withMinConcurrentReconciliationThreads(int threadNumber) {
    this.minConcurrentReconciliationThreads = Utils.ensureValid(threadNumber,
        "minimum reconciliation threads", ExecutorServiceManager.MIN_THREAD_NUMBER,
        original.minConcurrentReconciliationThreads());
    return this;
  }

  public ConfigurationServiceOverrider withMinConcurrentWorkflowExecutorThreads(int threadNumber) {
    this.minConcurrentWorkflowExecutorThreads = Utils.ensureValid(threadNumber,
        "minimum workflow execution threads", ExecutorServiceManager.MIN_THREAD_NUMBER,
        original.minConcurrentWorkflowExecutorThreads());
    return this;
  }

  public ConfigurationServiceOverrider withDependentResourceFactory(
      DependentResourceFactory dependentResourceFactory) {
    this.dependentResourceFactory = dependentResourceFactory;
    return this;
  }

  public ConfigurationServiceOverrider withResourceCloner(Cloner cloner) {
    this.cloner = cloner;
    return this;
  }

  public ConfigurationServiceOverrider withTerminationTimeoutSeconds(int timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
    return this;
  }

  public ConfigurationServiceOverrider withMetrics(Metrics metrics) {
    this.metrics = metrics;
    return this;
  }

  public ConfigurationServiceOverrider withCloseClientOnStop(boolean close) {
    this.closeClientOnStop = close;
    return this;
  }

  public ConfigurationServiceOverrider withExecutorService(ExecutorService executorService) {
    this.executorService = executorService;
    return this;
  }

  public ConfigurationServiceOverrider withWorkflowExecutorService(
      ExecutorService workflowExecutorService) {
    this.workflowExecutorService = workflowExecutorService;
    return this;
  }

  /**
   * Replaces the default {@link KubernetesClient} instance by the specified one. This is the
   * preferred mechanism to configure which client will be used to access the cluster.
   *
   * @param client the fully configured client to use for cluster access
   * @return this {@link ConfigurationServiceOverrider} for chained customization
   */
  public ConfigurationServiceOverrider withKubernetesClient(KubernetesClient client) {
    this.client = client;
    return this;
  }

  public ConfigurationServiceOverrider withLeaderElectionConfiguration(
      LeaderElectionConfiguration leaderElectionConfiguration) {
    this.leaderElectionConfiguration = leaderElectionConfiguration;
    return this;
  }

  public ConfigurationServiceOverrider withInformerStoppedHandler(InformerStoppedHandler handler) {
    this.informerStoppedHandler = handler;
    return this;
  }

  public ConfigurationServiceOverrider withStopOnInformerErrorDuringStartup(
      boolean stopOnInformerErrorDuringStartup) {
    this.stopOnInformerErrorDuringStartup = stopOnInformerErrorDuringStartup;
    return this;
  }

  public ConfigurationServiceOverrider withCacheSyncTimeout(Duration cacheSyncTimeout) {
    this.cacheSyncTimeout = cacheSyncTimeout;
    return this;
  }

  public ConfigurationServiceOverrider withResourceClassResolver(
      ResourceClassResolver resourceClassResolver) {
    this.resourceClassResolver = resourceClassResolver;
    return this;
  }

  public ConfigurationServiceOverrider withSSABasedCreateUpdateMatchForDependentResources(
      boolean value) {
    this.ssaBasedCreateUpdateMatchForDependentResources = value;
    return this;
  }

  public ConfigurationServiceOverrider withDefaultNonSSAResource(
      Set<Class<? extends HasMetadata>> defaultNonSSAResource) {
    this.defaultNonSSAResource = defaultNonSSAResource;
    return this;
  }

  public ConfigurationServiceOverrider withPreviousAnnotationForDependentResources(
      boolean value) {
    this.previousAnnotationForDependentResources = value;
    return this;
  }

  public ConfigurationServiceOverrider wihtParseResourceVersions(
      boolean value) {
    this.parseResourceVersions = value;
    return this;
  }

  public ConfigurationService build() {
    return new BaseConfigurationService(original.getVersion(), cloner, client) {
      @Override
      public Set<String> getKnownReconcilerNames() {
        return original.getKnownReconcilerNames();
      }

      @Override
      public boolean checkCRDAndValidateLocalModel() {
        return checkCR != null ? checkCR : original.checkCRDAndValidateLocalModel();
      }

      @Override
      public DependentResourceFactory dependentResourceFactory() {
        return dependentResourceFactory != null ? dependentResourceFactory
            : DependentResourceFactory.DEFAULT;
      }

      @Override
      public int concurrentReconciliationThreads() {
        return Utils.ensureValid(
            concurrentReconciliationThreads != null ? concurrentReconciliationThreads
                : original.concurrentReconciliationThreads(),
            "maximum reconciliation threads",
            minimumMaxValueFor(minConcurrentReconciliationThreads),
            original.concurrentReconciliationThreads());
      }

      @Override
      public int concurrentWorkflowExecutorThreads() {
        return Utils.ensureValid(
            concurrentWorkflowExecutorThreads != null ? concurrentWorkflowExecutorThreads
                : original.concurrentWorkflowExecutorThreads(),
            "maximum workflow execution threads",
            minimumMaxValueFor(minConcurrentWorkflowExecutorThreads),
            original.concurrentWorkflowExecutorThreads());
      }

      @Override
      public int minConcurrentReconciliationThreads() {
        return minConcurrentReconciliationThreads != null ? minConcurrentReconciliationThreads
            : original.minConcurrentReconciliationThreads();
      }

      @Override
      public int minConcurrentWorkflowExecutorThreads() {
        return minConcurrentWorkflowExecutorThreads != null ? minConcurrentWorkflowExecutorThreads
            : original.minConcurrentWorkflowExecutorThreads();
      }

      @Override
      public int getTerminationTimeoutSeconds() {
        return timeoutSeconds != null ? timeoutSeconds : original.getTerminationTimeoutSeconds();
      }

      @Override
      public Metrics getMetrics() {
        return metrics != null ? metrics : original.getMetrics();
      }

      @Override
      public boolean closeClientOnStop() {
        return closeClientOnStop != null ? closeClientOnStop : original.closeClientOnStop();
      }

      @Override
      public ExecutorService getExecutorService() {
        return executorService != null ? executorService
            : super.getExecutorService();
      }

      @Override
      public ExecutorService getWorkflowExecutorService() {
        return workflowExecutorService != null ? workflowExecutorService
            : super.getWorkflowExecutorService();
      }

      @Override
      public Optional<LeaderElectionConfiguration> getLeaderElectionConfiguration() {
        return leaderElectionConfiguration != null ? Optional.of(leaderElectionConfiguration)
            : original.getLeaderElectionConfiguration();
      }

      @Override
      public Optional<InformerStoppedHandler> getInformerStoppedHandler() {
        return informerStoppedHandler != null ? Optional.of(informerStoppedHandler)
            : original.getInformerStoppedHandler();
      }

      @Override
      public boolean stopOnInformerErrorDuringStartup() {
        return stopOnInformerErrorDuringStartup != null ? stopOnInformerErrorDuringStartup
            : super.stopOnInformerErrorDuringStartup();
      }

      @Override
      public Duration cacheSyncTimeout() {
        return cacheSyncTimeout != null ? cacheSyncTimeout : super.cacheSyncTimeout();
      }

      @Override
      public ResourceClassResolver getResourceClassResolver() {
        return resourceClassResolver != null ? resourceClassResolver
            : super.getResourceClassResolver();
      }

      @Override
      public boolean ssaBasedCreateUpdateMatchForDependentResources() {
        return ssaBasedCreateUpdateMatchForDependentResources != null
            ? ssaBasedCreateUpdateMatchForDependentResources
            : super.ssaBasedCreateUpdateMatchForDependentResources();
      }

      @Override
      public Set<Class<? extends HasMetadata>> defaultNonSSAResource() {
        return defaultNonSSAResource != null ? defaultNonSSAResource
            : super.defaultNonSSAResource();
      }

      @Override
      public boolean previousAnnotationForDependentResourcesEventFiltering() {
        return previousAnnotationForDependentResources != null
            ? previousAnnotationForDependentResources
            : super.previousAnnotationForDependentResourcesEventFiltering();
      }

      @Override
      public boolean parseResourceVersionsForEventFilteringAndCaching() {
        return parseResourceVersions != null
            ? parseResourceVersions
            : super.parseResourceVersionsForEventFilteringAndCaching();
      }
    };
  }

  /**
   * @deprecated Use
   *             {@link ConfigurationService#newOverriddenConfigurationService(ConfigurationService, Consumer)}
   *             instead
   * @param original that will be overridden
   * @return current overrider
   */
  @Deprecated(since = "2.2.0")
  public static ConfigurationServiceOverrider override(ConfigurationService original) {
    return new ConfigurationServiceOverrider(original);
  }
}

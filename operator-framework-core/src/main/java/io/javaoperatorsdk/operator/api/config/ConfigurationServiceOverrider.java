package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

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
  private Integer concurrentWorkflowExecutorThreads;
  private Cloner cloner;
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
  private Boolean useSSAToPatchPrimaryResource;
  private Boolean cloneSecondaryResourcesWhenGettingFromCache;
  @SuppressWarnings("rawtypes")
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

  @SuppressWarnings("rawtypes")
  public ConfigurationServiceOverrider withDependentResourceFactory(
      DependentResourceFactory dependentResourceFactory) {
    this.dependentResourceFactory = dependentResourceFactory;
    return this;
  }

  public ConfigurationServiceOverrider withResourceCloner(Cloner cloner) {
    this.cloner = cloner;
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

  public ConfigurationServiceOverrider withParseResourceVersions(
      boolean value) {
    this.parseResourceVersions = value;
    return this;
  }

  public ConfigurationServiceOverrider withUseSSAToPatchPrimaryResource(boolean value) {
    this.useSSAToPatchPrimaryResource = value;
    return this;
  }

  public ConfigurationServiceOverrider withCloneSecondaryResourcesWhenGettingFromCache(
      boolean value) {
    this.cloneSecondaryResourcesWhenGettingFromCache = value;
    return this;
  }

  public ConfigurationService build() {
    return new BaseConfigurationService(original.getVersion(), cloner, client) {
      @Override
      public Set<String> getKnownReconcilerNames() {
        return original.getKnownReconcilerNames();
      }

      private <T> T overriddenValueOrDefault(T value,
          Function<ConfigurationService, T> defaultValue) {
        return value != null ? value : defaultValue.apply(original);
      }

      @Override
      public boolean checkCRDAndValidateLocalModel() {
        return overriddenValueOrDefault(checkCR,
            ConfigurationService::checkCRDAndValidateLocalModel);
      }

      @Override
      @SuppressWarnings("rawtypes")
      public DependentResourceFactory dependentResourceFactory() {
        return overriddenValueOrDefault(dependentResourceFactory,
            ConfigurationService::dependentResourceFactory);
      }

      @Override
      public int concurrentReconciliationThreads() {
        return Utils.ensureValid(
            overriddenValueOrDefault(concurrentReconciliationThreads,
                ConfigurationService::concurrentReconciliationThreads),
            "maximum reconciliation threads",
            1,
            original.concurrentReconciliationThreads());
      }

      @Override
      public int concurrentWorkflowExecutorThreads() {
        return Utils.ensureValid(
            overriddenValueOrDefault(concurrentWorkflowExecutorThreads,
                ConfigurationService::concurrentWorkflowExecutorThreads),
            "maximum workflow execution threads",
            1,
            original.concurrentWorkflowExecutorThreads());
      }

      @Override
      public Metrics getMetrics() {
        return overriddenValueOrDefault(metrics, ConfigurationService::getMetrics);
      }

      @Override
      public boolean closeClientOnStop() {
        return overriddenValueOrDefault(closeClientOnStop, ConfigurationService::closeClientOnStop);
      }

      @Override
      public ExecutorService getExecutorService() {
        return overriddenValueOrDefault(executorService, ConfigurationService::getExecutorService);
      }

      @Override
      public ExecutorService getWorkflowExecutorService() {
        return overriddenValueOrDefault(workflowExecutorService,
            ConfigurationService::getWorkflowExecutorService);
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
        return overriddenValueOrDefault(stopOnInformerErrorDuringStartup,
            ConfigurationService::stopOnInformerErrorDuringStartup);
      }

      @Override
      public Duration cacheSyncTimeout() {
        return overriddenValueOrDefault(cacheSyncTimeout, ConfigurationService::cacheSyncTimeout);
      }

      @Override
      public ResourceClassResolver getResourceClassResolver() {
        return overriddenValueOrDefault(resourceClassResolver,
            ConfigurationService::getResourceClassResolver);
      }

      @Override
      public boolean ssaBasedCreateUpdateMatchForDependentResources() {
        return overriddenValueOrDefault(ssaBasedCreateUpdateMatchForDependentResources,
            ConfigurationService::ssaBasedCreateUpdateMatchForDependentResources);
      }

      @Override
      public Set<Class<? extends HasMetadata>> defaultNonSSAResource() {
        return overriddenValueOrDefault(defaultNonSSAResource,
            ConfigurationService::defaultNonSSAResource);
      }

      @Override
      public boolean previousAnnotationForDependentResourcesEventFiltering() {
        return overriddenValueOrDefault(previousAnnotationForDependentResources,
            ConfigurationService::previousAnnotationForDependentResourcesEventFiltering);
      }

      @Override
      public boolean parseResourceVersionsForEventFilteringAndCaching() {
        return overriddenValueOrDefault(parseResourceVersions,
            ConfigurationService::parseResourceVersionsForEventFilteringAndCaching);
      }

      @Override
      public boolean useSSAToPatchPrimaryResource() {
        return overriddenValueOrDefault(useSSAToPatchPrimaryResource,
            ConfigurationService::useSSAToPatchPrimaryResource);
      }

      @Override
      public boolean cloneSecondaryResourcesWhenGettingFromCache() {
        return overriddenValueOrDefault(cloneSecondaryResourcesWhenGettingFromCache,
            ConfigurationService::cloneSecondaryResourcesWhenGettingFromCache);
      }
    };
  }

}

package io.javaoperatorsdk.operator.api.config;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import io.fabric8.kubernetes.client.Config;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;

import com.fasterxml.jackson.databind.ObjectMapper;

@SuppressWarnings("unused")
public class ConfigurationServiceOverrider {
  private final ConfigurationService original;
  private Metrics metrics;
  private Config clientConfig;
  private Boolean checkCR;
  private Integer threadNumber;
  private Cloner cloner;
  private Integer timeoutSeconds;
  private Boolean closeClientOnStop;
  private ObjectMapper objectMapper;
  private ExecutorService executorService;
  private ExecutorService workflowExecutorService;
  private LeaderElectionConfiguration leaderElectionConfiguration;

  ConfigurationServiceOverrider(ConfigurationService original) {
    this.original = original;
  }

  public ConfigurationServiceOverrider withClientConfiguration(Config configuration) {
    this.clientConfig = configuration;
    return this;
  }

  public ConfigurationServiceOverrider checkingCRDAndValidateLocalModel(boolean check) {
    this.checkCR = check;
    return this;
  }

  public ConfigurationServiceOverrider withConcurrentReconciliationThreads(int threadNumber) {
    this.threadNumber = threadNumber;
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

  public ConfigurationServiceOverrider withObjectMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    return this;
  }

  public ConfigurationServiceOverrider withLeaderElectionConfiguration(
      LeaderElectionConfiguration leaderElectionConfiguration) {
    this.leaderElectionConfiguration = leaderElectionConfiguration;
    return this;
  }

  public ConfigurationService build() {
    return new BaseConfigurationService(original.getVersion(), cloner, objectMapper) {
      @Override
      public Set<String> getKnownReconcilerNames() {
        return original.getKnownReconcilerNames();
      }

      @Override
      public Config getClientConfiguration() {
        return clientConfig != null ? clientConfig : original.getClientConfiguration();
      }

      @Override
      public boolean checkCRDAndValidateLocalModel() {
        return checkCR != null ? checkCR : original.checkCRDAndValidateLocalModel();
      }

      @Override
      public int concurrentReconciliationThreads() {
        return threadNumber != null ? threadNumber : original.concurrentReconciliationThreads();
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
        return executorService != null ? executorService : original.getExecutorService();
      }

      @Override
      public ExecutorService getWorkflowExecutorService() {
        return workflowExecutorService != null ? workflowExecutorService
            : original.getWorkflowExecutorService();
      }

      @Override
      public ObjectMapper getObjectMapper() {
        return objectMapper != null ? objectMapper : original.getObjectMapper();
      }

      @Override
      public Optional<LeaderElectionConfiguration> getLeaderElectionConfiguration() {
        return leaderElectionConfiguration != null ? Optional.of(leaderElectionConfiguration)
            : original.getLeaderElectionConfiguration();
      }
    };
  }

  /**
   * @deprecated Use {@link ConfigurationServiceProvider#overrideCurrent(Consumer)} instead
   */
  @Deprecated(since = "2.2.0")
  public static ConfigurationServiceOverrider override(ConfigurationService original) {
    return new ConfigurationServiceOverrider(original);
  }
}

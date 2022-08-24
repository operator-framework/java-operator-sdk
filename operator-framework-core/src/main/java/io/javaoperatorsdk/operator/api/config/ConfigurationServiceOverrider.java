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
  private boolean checkCR;
  private int threadNumber;
  private Cloner cloner;
  private int timeoutSeconds;
  private boolean closeClientOnStop;
  private ObjectMapper objectMapper;
  private ExecutorService executorService = null;
  private LeaderElectionConfiguration leaderElectionConfiguration;

  ConfigurationServiceOverrider(ConfigurationService original) {
    this.original = original;
    this.clientConfig = original.getClientConfiguration();
    this.checkCR = original.checkCRDAndValidateLocalModel();
    this.threadNumber = original.concurrentReconciliationThreads();
    this.cloner = original.getResourceCloner();
    this.timeoutSeconds = original.getTerminationTimeoutSeconds();
    this.metrics = original.getMetrics();
    this.closeClientOnStop = original.closeClientOnStop();
    this.objectMapper = original.getObjectMapper();
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
        return clientConfig;
      }

      @Override
      public boolean checkCRDAndValidateLocalModel() {
        return checkCR;
      }

      @Override
      public int concurrentReconciliationThreads() {
        return threadNumber;
      }

      @Override
      public int getTerminationTimeoutSeconds() {
        return timeoutSeconds;
      }

      @Override
      public Metrics getMetrics() {
        return metrics;
      }

      @Override
      public boolean closeClientOnStop() {
        return closeClientOnStop;
      }

      @Override
      public ExecutorService getExecutorService() {
        if (executorService != null) {
          return executorService;
        } else {
          return super.getExecutorService();
        }
      }

      @Override
      public ObjectMapper getObjectMapper() {
        return objectMapper;
      }

      @Override
      public Optional<LeaderElectionConfiguration> getLeaderElectionConfiguration() {
        return Optional.ofNullable(leaderElectionConfiguration);
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

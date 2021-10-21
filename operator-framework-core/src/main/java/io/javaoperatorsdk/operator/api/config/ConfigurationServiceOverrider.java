package io.javaoperatorsdk.operator.api.config;

import java.util.Set;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;

public class ConfigurationServiceOverrider {
  private final ConfigurationService original;
  private Metrics metrics;
  private Config clientConfig;
  private boolean checkCR;
  private int threadNumber;
  private Cloner cloner;
  private int timeoutSeconds;

  public ConfigurationServiceOverrider(
      ConfigurationService original) {
    this.original = original;
    this.clientConfig = original.getClientConfiguration();
    this.checkCR = original.checkCRDAndValidateLocalModel();
    this.threadNumber = original.concurrentReconciliationThreads();
    this.cloner = original.getResourceCloner();
    this.timeoutSeconds = original.getTerminationTimeoutSeconds();
    this.metrics = original.getMetrics();
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

  public ConfigurationService build() {
    return new ConfigurationService() {
      @Override
      public <R extends CustomResource> ControllerConfiguration<R> getConfigurationFor(
          ResourceController<R> controller) {
        return original.getConfigurationFor(controller);
      }

      @Override
      public Set<String> getKnownControllerNames() {
        return original.getKnownControllerNames();
      }

      @Override
      public Version getVersion() {
        return original.getVersion();
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
      public Cloner getResourceCloner() {
        return cloner;
      }

      @Override
      public int getTerminationTimeoutSeconds() {
        return timeoutSeconds;
      }

      @Override
      public Metrics getMetrics() {
        return metrics;
      }
    };
  }

  public static ConfigurationServiceOverrider override(ConfigurationService original) {
    return new ConfigurationServiceOverrider(original);
  }
}

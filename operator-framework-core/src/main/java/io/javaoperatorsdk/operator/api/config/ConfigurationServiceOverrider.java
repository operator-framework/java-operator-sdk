package io.javaoperatorsdk.operator.api.config;

import java.util.Set;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.Metrics;
import io.javaoperatorsdk.operator.api.ResourceController;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ConfigurationServiceOverrider {
  private final ConfigurationService original;
  private Metrics metrics;
  private Config clientConfig;
  private boolean checkCR;
  private int threadNumber;
  private ObjectMapper mapper;
  private int timeoutSeconds;

  public ConfigurationServiceOverrider(
      ConfigurationService original) {
    this.original = original;
    this.clientConfig = original.getClientConfiguration();
    this.checkCR = original.checkCRDAndValidateLocalModel();
    this.threadNumber = original.concurrentReconciliationThreads();
    this.mapper = original.getObjectMapper();
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

  public ConfigurationServiceOverrider withObjectMapper(ObjectMapper mapper) {
    this.mapper = mapper;
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
      public ObjectMapper getObjectMapper() {
        return mapper;
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

package io.javaoperatorsdk.operator.api.config;

public interface ConfigurationServiceAware {

  ConfigurationService getConfigurationService();

  void setConfigurationService(ConfigurationService service);
}

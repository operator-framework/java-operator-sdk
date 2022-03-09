package io.javaoperatorsdk.operator.api.config;

public class ConfigurationServiceProvider {
  static ConfigurationService instance;
  private static ConfigurationService defaultConfigurationService;
  private static boolean alreadyConfigured = false;

  public static ConfigurationService instance() {
    if (instance == null) {
      if (defaultConfigurationService == null) {
        throw new IllegalStateException(
            "A ConfigurationService needs to be configured using the `set` method first");
      }
      set(defaultConfigurationService);
    }
    return instance;
  }

  public static void set(ConfigurationService instance) {
    set(instance, false);
  }

  static void set(ConfigurationService instance, boolean overriding) {
    if ((overriding && alreadyConfigured) ||
        (ConfigurationServiceProvider.instance != null
            && !ConfigurationServiceProvider.instance.equals(instance))) {
      throw new IllegalStateException(
          "A ConfigurationService has already been set and cannot be set again. Current: "
              + ConfigurationServiceProvider.instance.getClass().getCanonicalName());
    }

    if (overriding) {
      alreadyConfigured = true;
    }
    ConfigurationServiceProvider.instance = instance;
  }

  public static void setDefault(ConfigurationService defaultConfigurationService) {
    ConfigurationServiceProvider.defaultConfigurationService = defaultConfigurationService;
  }

  public static void reset() {
    instance = null;
  }
}

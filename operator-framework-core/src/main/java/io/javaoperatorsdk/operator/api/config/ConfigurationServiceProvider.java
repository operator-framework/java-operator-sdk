package io.javaoperatorsdk.operator.api.config;

import java.util.function.Consumer;

public class ConfigurationServiceProvider {
  static final ConfigurationService DEFAULT =
      new BaseConfigurationService(Utils.loadFromProperties());
  private static ConfigurationService instance;
  private static ConfigurationService defaultConfigurationService = DEFAULT;
  private static boolean alreadyConfigured = false;

  private ConfigurationServiceProvider() {}

  public synchronized static ConfigurationService instance() {
    if (instance == null) {
      set(defaultConfigurationService);
    }
    return instance;
  }

  public synchronized static void set(ConfigurationService instance) {
    set(instance, false);
  }

  private static void set(ConfigurationService instance, boolean overriding) {
    final var current = ConfigurationServiceProvider.instance;
    if (!overriding) {
      if (current != null && !current.equals(instance)) {
        throw new IllegalStateException(
            "A ConfigurationService has already been set and cannot be set again. Current: "
                + current.getClass().getCanonicalName());
      }
    } else {
      if (alreadyConfigured) {
        throw new IllegalStateException(
            "The ConfigurationService has already been overridden once and cannot be changed again. Current: "
                + current.getClass().getCanonicalName());
      } else {
        alreadyConfigured = true;
      }
    }

    ConfigurationServiceProvider.instance = instance;
  }

  public synchronized static void overrideCurrent(
      Consumer<ConfigurationServiceOverrider> overrider) {
    final var toOverride =
        new ConfigurationServiceOverrider(ConfigurationServiceProvider.instance());
    overrider.accept(toOverride);
    ConfigurationServiceProvider.set(toOverride.build(), true);
  }

  public synchronized static void setDefault(ConfigurationService defaultConfigurationService) {
    ConfigurationServiceProvider.defaultConfigurationService = defaultConfigurationService;
  }

  synchronized static ConfigurationService getDefault() {
    return defaultConfigurationService;
  }

  public synchronized static void reset() {
    defaultConfigurationService = DEFAULT;
    instance = null;
    alreadyConfigured = false;
  }
}

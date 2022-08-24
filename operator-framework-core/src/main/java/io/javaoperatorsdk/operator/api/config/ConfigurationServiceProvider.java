package io.javaoperatorsdk.operator.api.config;

import java.util.function.Consumer;

/**
 * For internal usage only, to avoid passing the operator configuration around. Preferred way to get
 * to the ConfigurationService is via the reconciliation context.
 */
public class ConfigurationServiceProvider {
  private static ConfigurationService instance;
  private static ConfigurationService defaultConfigurationService = createDefault();
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

  public synchronized static ConfigurationService overrideCurrent(
      Consumer<ConfigurationServiceOverrider> overrider) {
    if (overrider != null) {
      final var toOverride = new ConfigurationServiceOverrider(instance());
      overrider.accept(toOverride);
      set(toOverride.build(), true);
    }
    return instance();
  }

  public synchronized static void setDefault(ConfigurationService defaultConfigurationService) {
    ConfigurationServiceProvider.defaultConfigurationService = defaultConfigurationService;
  }

  synchronized static ConfigurationService getDefault() {
    return defaultConfigurationService;
  }

  public synchronized static void reset() {
    defaultConfigurationService = createDefault();
    instance = null;
    alreadyConfigured = false;
  }

  static ConfigurationService createDefault() {
    return new BaseConfigurationService(Utils.loadFromProperties());
  }
}

package io.javaoperatorsdk.operator.api.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.javaoperatorsdk.operator.api.config.ConfigurationService.DEFAULT_RECONCILIATION_THREADS_NUMBER;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationServiceProviderTest {
  @BeforeEach
  void resetProvider() {
    ConfigurationServiceProvider.reset();
  }

  @Test
  void shouldProvideADefaultInstanceIfNoneIsSet() {
    final var instance = ConfigurationServiceProvider.instance();
    assertNotNull(instance);
    assertTrue(instance instanceof BaseConfigurationService);
  }

  @Test
  void shouldProvideTheSetDefaultInstanceIfProvided() {
    final var defaultConfig = new AbstractConfigurationService(null);
    ConfigurationServiceProvider.setDefault(defaultConfig);
    assertEquals(defaultConfig, ConfigurationServiceProvider.instance());
  }

  @Test
  void shouldProvideTheSetInstanceIfProvided() {
    final var config = new AbstractConfigurationService(null);
    ConfigurationServiceProvider.set(config);
    assertEquals(config, ConfigurationServiceProvider.instance());
  }

  @Test
  void shouldBePossibleToOverrideConfigOnce() {
    final var config = new AbstractConfigurationService(null);
    assertThat(config.concurrentReconciliationThreads())
        .isEqualTo(DEFAULT_RECONCILIATION_THREADS_NUMBER);

    ConfigurationServiceProvider.set(config);
    var instance = ConfigurationServiceProvider.instance();
    assertEquals(config, instance);

    ConfigurationServiceProvider.overrideCurrent(o -> o.withConcurrentReconciliationThreads(10));
    instance = ConfigurationServiceProvider.instance();
    assertNotEquals(config, instance);
    assertThat(instance.concurrentReconciliationThreads()).isEqualTo(10);

    assertThrows(IllegalStateException.class,
        () -> ConfigurationServiceProvider.overrideCurrent(o -> o.withCloseClientOnStop(false)));
  }

  @Test
  void resetShouldResetAllState() {
    shouldBePossibleToOverrideConfigOnce();

    ConfigurationServiceProvider.reset();
    assertEquals(ConfigurationServiceProvider.DEFAULT, ConfigurationServiceProvider.getDefault());

    shouldBePossibleToOverrideConfigOnce();
  }
}

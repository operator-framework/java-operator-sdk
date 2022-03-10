package io.javaoperatorsdk.operator.api.config;

import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    assertTrue(config.checkCRDAndValidateLocalModel());

    ConfigurationServiceProvider.set(config);
    var instance = ConfigurationServiceProvider.instance();
    assertEquals(config, instance);

    ConfigurationServiceProvider.overrideCurrent(o -> o.checkingCRDAndValidateLocalModel(false));
    instance = ConfigurationServiceProvider.instance();
    assertNotEquals(config, instance);
    assertFalse(instance.checkCRDAndValidateLocalModel());

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

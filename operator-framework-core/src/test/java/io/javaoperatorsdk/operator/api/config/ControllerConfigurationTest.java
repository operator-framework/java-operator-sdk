package io.javaoperatorsdk.operator.api.config;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.junit.jupiter.api.Assertions.*;

class ControllerConfigurationTest {

  @Test
  void getCustomResourceClass() {
    final ControllerConfiguration<TestCustomResource> conf = new ControllerConfiguration<>() {
      @Override
      public String getAssociatedReconcilerClassName() {
        return null;
      }

      @Override
      public ConfigurationService getConfigurationService() {
        return null;
      }

      @Override
      public void setConfigurationService(ConfigurationService service) {}
    };
    assertEquals(TestCustomResource.class, conf.getResourceClass());
  }
}

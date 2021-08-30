package io.javaoperatorsdk.operator.api.config;

import static org.junit.jupiter.api.Assertions.*;

import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import org.junit.jupiter.api.Test;

class ControllerConfigurationTest {

  @Test
  void getCustomResourceClass() {
    final ControllerConfiguration<TestCustomResource> conf = new ControllerConfiguration<>() {
      @Override
      public String getAssociatedControllerClassName() {
        return null;
      }

      @Override
      public ConfigurationService getConfigurationService() {
        return null;
      }
    };
    assertEquals(TestCustomResource.class, conf.getCustomResourceClass());
  }
}

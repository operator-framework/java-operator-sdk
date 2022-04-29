package io.javaoperatorsdk.operator.api.config;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.junit.jupiter.api.Assertions.*;

class ControllerConfigurationTest {

  @Test
  void getCustomResourceClass() {
    final ControllerConfiguration<TestCustomResource> conf = () -> null;
    assertEquals(TestCustomResource.class, conf.getResourceClass());
  }
}

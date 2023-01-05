package io.javaoperatorsdk.operator.api.config;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ControllerConfigurationTest {

  @Test
  void getCustomResourceClass() {
    final ControllerConfiguration<TestCustomResource> lambdasCannotBeUsedToExtractGenericParam =
        () -> null;
    assertThrows(RuntimeException.class,
        lambdasCannotBeUsedToExtractGenericParam::getResourceClass);

    final ControllerConfiguration<TestCustomResource> conf = new ControllerConfiguration<>() {
      @Override
      public String getAssociatedReconcilerClassName() {
        return null;
      }
    };
    assertEquals(TestCustomResource.class, conf.getResourceClass());
  }
}

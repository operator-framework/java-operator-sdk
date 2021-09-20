package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.sample.simple.TestCustomResourceController;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControllerUtilsTest {

  @Test
  void getDefaultResourceControllerName() {
    assertEquals(
        "testcustomresourcecontroller",
        ControllerUtils.getDefaultResourceControllerName(
            TestCustomResourceController.class.getCanonicalName()));
  }
}

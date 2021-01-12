package io.javaoperatorsdk.operator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.javaoperatorsdk.operator.sample.simple.TestCustomResourceController;
import org.junit.jupiter.api.Test;

class ControllerUtilsTest {

  @Test
  void getDefaultResourceControllerName() {
    assertEquals(
        "testcustomresourcecontroller",
        ControllerUtils.getDefaultResourceControllerName(
            TestCustomResourceController.class.getCanonicalName()));
  }
}

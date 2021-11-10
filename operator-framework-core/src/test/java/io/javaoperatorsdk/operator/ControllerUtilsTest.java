package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.sample.simple.TestCustomReconciler;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControllerUtilsTest {

  @Test
  void getDefaultResourceControllerName() {
    assertEquals(
        "testcustomreconciler",
        ControllerUtils.getDefaultReconcilerName(
            TestCustomReconciler.class.getCanonicalName()));
  }
}

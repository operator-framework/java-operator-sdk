package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.sample.simple.TestCustomReconciler;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReconcilerUtilsTest {

  @Test
  void getDefaultResourceControllerName() {
    assertEquals(
        "testcustomreconciler",
        ReconcilerUtils.getDefaultReconcilerName(
            TestCustomReconciler.class.getCanonicalName()));
  }
}

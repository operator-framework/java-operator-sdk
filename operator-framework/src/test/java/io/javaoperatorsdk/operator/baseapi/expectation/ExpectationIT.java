package io.javaoperatorsdk.operator.baseapi.expectation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

class ExpectationIT {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(new ExpectationReconciler()).build();

  @Test
  void testExpectation() {}
}

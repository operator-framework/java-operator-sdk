package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.primarytosecondarydependent.PrimaryToSecondaryDependentReconciler;

class PrimaryToSecondaryForDependentIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new PrimaryToSecondaryDependentReconciler()).build();

  @Test
  void testReconcilePreconditionOnReadOnlyResource() {

  }

}

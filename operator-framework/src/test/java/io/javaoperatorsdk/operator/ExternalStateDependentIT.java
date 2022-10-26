package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.externalstate.ExternalStateDependentReconciler;

class ExternalStateDependentIT extends ExternalStateTestBase {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(ExternalStateDependentReconciler.class)
          .build();

  @Override
  LocallyRunOperatorExtension extension() {
    return operator;
  }
}

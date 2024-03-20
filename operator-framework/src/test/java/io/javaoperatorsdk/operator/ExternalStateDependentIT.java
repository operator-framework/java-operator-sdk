package io.javaoperatorsdk.operator;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.externalstate.ExternalStateDependentReconciler;
import org.junit.jupiter.api.extension.RegisterExtension;

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

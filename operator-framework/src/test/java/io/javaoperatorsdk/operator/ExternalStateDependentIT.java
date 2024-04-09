package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.externalstate.ExternalStateDependentReconciler;

import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
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

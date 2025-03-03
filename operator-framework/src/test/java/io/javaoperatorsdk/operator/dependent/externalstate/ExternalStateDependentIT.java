package io.javaoperatorsdk.operator.dependent.externalstate;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

public class ExternalStateDependentIT extends ExternalStateTestBase {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(ExternalStateDependentReconciler.class)
          .build();

  @Override
  public LocallyRunOperatorExtension extension() {
    return operator;
  }
}

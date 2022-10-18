package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.externalstate.ExternalStateReconciler;

class ExternalStateIT extends ExternalStateTestBase {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(ExternalStateReconciler.class)
          .build();

  @Override
  LocallyRunOperatorExtension extension() {
    return operator;
  }
}

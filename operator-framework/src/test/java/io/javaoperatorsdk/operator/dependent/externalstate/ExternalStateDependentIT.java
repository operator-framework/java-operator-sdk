package io.javaoperatorsdk.operator.dependent.externalstate;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

@Sample(
    tldr = "External State Tracking in Dependent Resources",
    description =
        """
        Demonstrates managing dependent resources with external state that needs to be tracked \
        independently of Kubernetes resources. This pattern allows operators to maintain state \
        information for external systems or resources, ensuring proper reconciliation even when \
        the external state differs from the desired Kubernetes resource state.
        """)
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

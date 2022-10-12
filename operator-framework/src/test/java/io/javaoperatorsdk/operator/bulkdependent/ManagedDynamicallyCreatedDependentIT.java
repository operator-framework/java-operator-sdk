package io.javaoperatorsdk.operator.bulkdependent;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.bulkdependent.ManagedDynamicDependentReconciler;

class ManagedDynamicallyCreatedDependentIT extends DynamicallyCreatedDependentTestBase {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(new ManagedDynamicDependentReconciler())
          .build();


  @Override
  LocallyRunOperatorExtension extension() {
    return extension;
  }
}

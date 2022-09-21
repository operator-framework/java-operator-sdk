package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.bulkdependent.ManagedBulkDependentReconciler;

class ManagedBulkDependentIT extends BulkDependentTestBase {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(new ManagedBulkDependentReconciler())
          .build();


  @Override
  LocallyRunOperatorExtension extension() {
    return extension;
  }
}

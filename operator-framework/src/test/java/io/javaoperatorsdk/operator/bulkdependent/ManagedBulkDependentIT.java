package io.javaoperatorsdk.operator.bulkdependent;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.bulkdependent.ManagedBulkDependentReconciler;
import org.junit.jupiter.api.extension.RegisterExtension;

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

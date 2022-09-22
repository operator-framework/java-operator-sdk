package io.javaoperatorsdk.operator.bulkdependent;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.bulkdependent.StandaloneBulkDependentReconciler;

class StandaloneBulkDependentIT extends BulkDependentTestBase {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(new StandaloneBulkDependentReconciler())
          .build();

  @Override
  LocallyRunOperatorExtension extension() {
    return extension;
  }
}

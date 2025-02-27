package io.javaoperatorsdk.operator.dependent.bulkdependent.standalone;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestBase;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

class StandaloneBulkDependentIT extends BulkDependentTestBase {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new StandaloneBulkDependentReconciler())
          .build();

  @Override
  public LocallyRunOperatorExtension extension() {
    return extension;
  }
}

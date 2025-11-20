package io.javaoperatorsdk.operator.dependent.bulkdependent.standalone;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestBase;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

@Sample(
    tldr = "Standalone Bulk Dependent Resources",
    description =
        "Demonstrates how to use standalone bulk dependent resources to manage multiple"
            + " resources of the same type efficiently. This test shows how bulk operations can be"
            + " performed on a collection of resources without individual reconciliation cycles,"
            + " improving performance when managing many similar resources.")
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

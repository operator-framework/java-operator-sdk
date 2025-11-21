package io.javaoperatorsdk.operator.dependent.bulkdependent.managed;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestBase;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

@Sample(
    tldr = "Bulk Dependent Resources with Managed Workflow",
    description =
        """
        Demonstrates how to manage bulk dependent resources using the managed workflow approach. \
        This test extends the base bulk dependent test to show how multiple instances of \
        the same type of dependent resource can be created and managed together. The \
        managed workflow handles the orchestration of creating, updating, and deleting \
        multiple dependent resources based on the primary resource specification.
        """)
public class ManagedBulkDependentIT extends BulkDependentTestBase {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new ManagedBulkDependentReconciler())
          .build();

  @Override
  public LocallyRunOperatorExtension extension() {
    return extension;
  }
}

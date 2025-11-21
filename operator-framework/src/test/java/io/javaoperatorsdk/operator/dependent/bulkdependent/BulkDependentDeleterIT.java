package io.javaoperatorsdk.operator.dependent.bulkdependent;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.dependent.bulkdependent.managed.ManagedDeleterBulkReconciler;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

@Sample(
    tldr = "Bulk Dependent Resource Deleter Implementation",
    description =
        """
        Demonstrates implementation of a bulk dependent resource with custom deleter logic. \
        This test extends BulkDependentTestBase to verify that bulk dependent resources can \
        implement custom deletion strategies, managing multiple resources efficiently during \
        cleanup operations.
        """)
public class BulkDependentDeleterIT extends BulkDependentTestBase {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new ManagedDeleterBulkReconciler())
          .build();

  @Override
  public LocallyRunOperatorExtension extension() {
    return extension;
  }
}

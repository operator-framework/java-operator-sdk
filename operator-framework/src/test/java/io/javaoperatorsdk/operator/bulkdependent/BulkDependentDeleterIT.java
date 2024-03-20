package io.javaoperatorsdk.operator.bulkdependent;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.bulkdependent.ManagedDeleterBulkReconciler;
import org.junit.jupiter.api.extension.RegisterExtension;

public class BulkDependentDeleterIT extends BulkDependentTestBase {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(new ManagedDeleterBulkReconciler())
          .build();

  @Override
  LocallyRunOperatorExtension extension() {
    return extension;
  }
}

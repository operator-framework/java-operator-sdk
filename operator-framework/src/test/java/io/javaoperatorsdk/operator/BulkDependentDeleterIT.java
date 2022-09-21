package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.bulkdependent.ManagedDeleterBulkReconciler;

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

package io.javaoperatorsdk.operator.dependent.bulkdependent;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.dependent.bulkdependent.managed.ManagedDeleterBulkReconciler;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

public class BulkDependentDeleterIT extends BulkDependentTestBase {

  @RegisterExtension
  static LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new ManagedDeleterBulkReconciler())
          .build();

  @Override
  public LocallyRunOperatorExtension extension() {
    return extension;
  }
}

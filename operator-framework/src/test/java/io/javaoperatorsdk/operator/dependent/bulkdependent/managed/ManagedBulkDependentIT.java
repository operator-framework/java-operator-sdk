package io.javaoperatorsdk.operator.dependent.bulkdependent.managed;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestBase;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

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

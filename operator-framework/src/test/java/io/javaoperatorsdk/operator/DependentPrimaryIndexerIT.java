package io.javaoperatorsdk.operator;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.primaryindexer.DependentPrimaryIndexerTestReconciler;

public class DependentPrimaryIndexerIT extends PrimaryIndexerIT {

  protected LocallyRunOperatorExtension buildOperator() {
    return LocallyRunOperatorExtension.builder()
        .withReconciler(new DependentPrimaryIndexerTestReconciler())
        .build();
  }
}

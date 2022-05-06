package io.javaoperatorsdk.operator;

import io.javaoperatorsdk.operator.junit.LocalOperatorExtension;
import io.javaoperatorsdk.operator.sample.primaryindexer.DependentPrimaryIndexerTestReconciler;

public class DependentPrimaryIndexerIT extends PrimaryIndexerIT {

  protected LocalOperatorExtension buildOperator() {
    return LocalOperatorExtension.builder()
        .withReconciler(new DependentPrimaryIndexerTestReconciler())
        .build();
  }
}

package io.javaoperatorsdk.operator;

import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.sample.primaryindexer.DependentPrimaryIndexerTestReconciler;

public class DependentPrimaryIndexerIT extends PrimaryIndexerIT {

  protected OperatorExtension buildOperator() {
    return OperatorExtension.builder().withReconciler(new DependentPrimaryIndexerTestReconciler())
        .build();
  }
}

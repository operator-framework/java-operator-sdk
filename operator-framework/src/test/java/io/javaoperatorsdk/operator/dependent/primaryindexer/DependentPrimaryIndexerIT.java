package io.javaoperatorsdk.operator.dependent.primaryindexer;

import io.javaoperatorsdk.operator.baseapi.primaryindexer.PrimaryIndexerIT;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

public class DependentPrimaryIndexerIT extends PrimaryIndexerIT {

  protected LocallyRunOperatorExtension buildOperator() {
    return LocallyRunOperatorExtension.builder()
        .withReconciler(new DependentPrimaryIndexerTestReconciler()).build();
  }
}

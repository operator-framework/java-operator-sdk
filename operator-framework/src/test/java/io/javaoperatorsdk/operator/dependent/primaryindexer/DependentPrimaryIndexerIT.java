package io.javaoperatorsdk.operator.dependent.primaryindexer;

import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.baseapi.primaryindexer.PrimaryIndexerIT;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

@Sample(
    tldr = "Primary Resource Indexer with Dependent Resources",
    description =
        """
        Extends PrimaryIndexerIT to test primary resource indexing functionality with dependent \
        resources. Demonstrates how custom indexes on primary resources can be used to efficiently \
        query and access resources within dependent resource implementations, enabling performant \
        lookups.
        """)
public class DependentPrimaryIndexerIT extends PrimaryIndexerIT {

  protected LocallyRunOperatorExtension buildOperator() {
    return LocallyRunOperatorExtension.builder()
        .withReconciler(new DependentPrimaryIndexerTestReconciler())
        .build();
  }
}

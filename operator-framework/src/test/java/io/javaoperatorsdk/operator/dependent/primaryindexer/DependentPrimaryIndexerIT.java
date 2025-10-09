/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

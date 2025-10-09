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
package io.javaoperatorsdk.operator.dependent.bulkdependent.managed;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestBase;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

@Sample(
    tldr = "Bulk Dependent Resources with Managed Workflow",
    description =
        """
        Demonstrates how to manage bulk dependent resources using the managed workflow approach. \
        This test extends the base bulk dependent test to show how multiple instances of \
        the same type of dependent resource can be created and managed together. The \
        managed workflow handles the orchestration of creating, updating, and deleting \
        multiple dependent resources based on the primary resource specification.
        """)
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

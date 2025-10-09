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
package io.javaoperatorsdk.operator.dependent.bulkdependent.condition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestCustomResource;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestBase.INITIAL_NUMBER_OF_CONFIG_MAPS;
import static io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestBase.testResource;
import static io.javaoperatorsdk.operator.dependent.bulkdependent.ConfigMapDeleterBulkDependentResource.LABEL_KEY;
import static io.javaoperatorsdk.operator.dependent.bulkdependent.ConfigMapDeleterBulkDependentResource.LABEL_VALUE;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Bulk Dependent Resources with Ready Conditions",
    description =
        """
        Tests bulk dependent resources with preconditions that control when reconciliation \
        occurs. This demonstrates using ready conditions to ensure bulk operations only execute \
        when the primary resource is in the appropriate state, coordinating complex multi-resource \
        management.
        """)
class BulkDependentWithConditionIT {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new ManagedBulkDependentWithReadyConditionReconciler())
          .build();

  @Test
  void handlesBulkDependentWithPrecondition() {
    var resource = testResource();
    extension.create(resource);

    await()
        .untilAsserted(
            () -> {
              var res =
                  extension.get(
                      BulkDependentTestCustomResource.class,
                      testResource().getMetadata().getName());
              assertThat(res.getStatus()).isNotNull();
              assertThat(res.getStatus().getReady()).isTrue();

              var cms =
                  extension
                      .getKubernetesClient()
                      .configMaps()
                      .inNamespace(extension.getNamespace())
                      .withLabel(LABEL_KEY, LABEL_VALUE)
                      .list()
                      .getItems();
              assertThat(cms).hasSize(INITIAL_NUMBER_OF_CONFIG_MAPS);
            });
  }
}

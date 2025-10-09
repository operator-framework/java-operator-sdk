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
package io.javaoperatorsdk.operator.dependent.multiplemanageddependentsametype;

import java.time.Duration;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.IntegrationTestConstants.GARBAGE_COLLECTION_TIMEOUT_SECONDS;
import static io.javaoperatorsdk.operator.dependent.multiplemanageddependentsametype.MultipleManagedDependentResourceReconciler.DATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Managing Multiple Dependent Resources of the Same Type",
    description =
        """
        Demonstrates how to manage multiple dependent resources of the same type from a single \
        reconciler. This test shows how multiple ConfigMaps with the same type can be \
        created, updated, and deleted as dependent resources of a custom resource, \
        verifying proper CRUD operations and garbage collection.
        """)
class MultipleManagedDependentSameTypeIT {

  public static final String TEST_RESOURCE_NAME = "test1";
  public static final String DEFAULT_SPEC_VALUE = "val";
  public static final String UPDATED_SPEC_VALUE = "updated-val";
  public static final int SECONDS = 30;

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new MultipleManagedDependentResourceReconciler())
          .build();

  @Test
  void handlesCrudOperations() {
    operator.create(testResource());
    assertConfigMapsPresent(DEFAULT_SPEC_VALUE);

    var updatedResource = testResource();
    updatedResource.getSpec().setValue(UPDATED_SPEC_VALUE);
    operator.replace(updatedResource);
    assertConfigMapsPresent(UPDATED_SPEC_VALUE);

    operator.delete(testResource());
    assertConfigMapsDeleted();
  }

  private void assertConfigMapsPresent(String expectedData) {
    await()
        .untilAsserted(
            () -> {
              var maps =
                  operator
                      .getKubernetesClient()
                      .configMaps()
                      .inNamespace(operator.getNamespace())
                      .list()
                      .getItems()
                      .stream()
                      .filter(cm -> cm.getMetadata().getName().startsWith(TEST_RESOURCE_NAME))
                      .collect(Collectors.toList());
              assertThat(maps).hasSize(2);
              assertThat(maps).allMatch(cm -> cm.getData().get(DATA_KEY).equals(expectedData));
            });
  }

  private void assertConfigMapsDeleted() {
    await()
        .atMost(Duration.ofSeconds(GARBAGE_COLLECTION_TIMEOUT_SECONDS))
        .untilAsserted(
            () -> {
              var maps =
                  operator
                      .getKubernetesClient()
                      .configMaps()
                      .inNamespace(operator.getNamespace())
                      .list()
                      .getItems()
                      .stream()
                      .filter(cm -> cm.getMetadata().getName().startsWith(TEST_RESOURCE_NAME))
                      .collect(Collectors.toList());
              assertThat(maps).hasSize(0);
            });
  }

  private MultipleManagedDependentResourceCustomResource testResource() {
    var res = new MultipleManagedDependentResourceCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());

    res.setSpec(new MultipleManagedDependentResourceSpec());
    res.getSpec().setValue(DEFAULT_SPEC_VALUE);
    return res;
  }
}

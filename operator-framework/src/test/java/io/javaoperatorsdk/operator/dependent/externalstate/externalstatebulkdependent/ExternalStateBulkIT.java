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
package io.javaoperatorsdk.operator.dependent.externalstate.externalstatebulkdependent;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.support.ExternalIDGenServiceMock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ExternalStateBulkIT {

  private static final String TEST_RESOURCE_NAME = "test1";

  public static final String INITIAL_TEST_DATA = "initialTestData";
  public static final String UPDATED_DATA = "updatedData";
  public static final int INITIAL_BULK_SIZE = 3;
  public static final int INCREASED_BULK_SIZE = 4;
  public static final int DECREASED_BULK_SIZE = 2;

  private final ExternalIDGenServiceMock externalService = ExternalIDGenServiceMock.getInstance();

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(ExternalStateBulkDependentReconciler.class)
          .build();

  @Test
  void reconcilesResourceWithPersistentState() {
    var resource = operator.create(testResource());
    assertResources(resource, INITIAL_TEST_DATA, INITIAL_BULK_SIZE);

    resource.getSpec().setData(UPDATED_DATA);
    resource = operator.replace(resource);
    assertResources(resource, UPDATED_DATA, INITIAL_BULK_SIZE);

    resource.getSpec().setNumber(INCREASED_BULK_SIZE);
    resource = operator.replace(resource);
    assertResources(resource, UPDATED_DATA, INCREASED_BULK_SIZE);

    resource.getSpec().setNumber(DECREASED_BULK_SIZE);
    resource = operator.replace(resource);
    assertResources(resource, UPDATED_DATA, DECREASED_BULK_SIZE);

    operator.delete(resource);
    assertResourcesDeleted(resource);
  }

  private void assertResourcesDeleted(ExternalStateBulkDependentCustomResource resource) {
    await()
        .untilAsserted(
            () -> {
              var configMaps =
                  operator
                      .getKubernetesClient()
                      .configMaps()
                      .inNamespace(operator.getNamespace())
                      .list()
                      .getItems()
                      .stream()
                      .filter(
                          cm ->
                              cm.getMetadata()
                                  .getName()
                                  .startsWith(resource.getMetadata().getName()));
              var resources = externalService.listResources();
              assertThat(configMaps).isEmpty();
              assertThat(resources).isEmpty();
            });
  }

  private void assertResources(
      ExternalStateBulkDependentCustomResource resource, String initialTestData, int size) {
    await()
        .pollInterval(Duration.ofMillis(700))
        .untilAsserted(
            () -> {
              var resources = externalService.listResources();
              assertThat(resources).hasSize(size);
              assertThat(resources).allMatch(r -> r.getData().startsWith(initialTestData));

              var configMaps =
                  operator
                      .getKubernetesClient()
                      .configMaps()
                      .inNamespace(operator.getNamespace())
                      .list()
                      .getItems()
                      .stream()
                      .filter(
                          cm ->
                              cm.getMetadata()
                                  .getName()
                                  .startsWith(resource.getMetadata().getName()));
              assertThat(configMaps).hasSize(size);
            });
  }

  private ExternalStateBulkDependentCustomResource testResource() {
    var res = new ExternalStateBulkDependentCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());

    res.setSpec(new ExternalStateBulkSpec());
    res.getSpec().setNumber(INITIAL_BULK_SIZE);
    res.getSpec().setData(INITIAL_TEST_DATA);
    return res;
  }
}

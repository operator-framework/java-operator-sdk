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
package io.javaoperatorsdk.operator.dependent.externalstate;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.support.ExternalIDGenServiceMock;

import static io.javaoperatorsdk.operator.dependent.externalstate.ExternalStateReconciler.ID_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public abstract class ExternalStateTestBase {

  private static final String TEST_RESOURCE_NAME = "test1";

  public static final String INITIAL_TEST_DATA = "initialTestData";
  public static final String UPDATED_DATA = "updatedData";

  private final ExternalIDGenServiceMock externalService = ExternalIDGenServiceMock.getInstance();

  @Test
  public void reconcilesResourceWithPersistentState() {
    var resource = extension().create(testResource());
    assertResourcesCreated(resource, INITIAL_TEST_DATA);

    resource.getSpec().setData(UPDATED_DATA);
    extension().replace(resource);
    assertResourcesCreated(resource, UPDATED_DATA);

    extension().delete(resource);
    assertResourcesDeleted(resource);
  }

  private void assertResourcesDeleted(ExternalStateCustomResource resource) {
    await()
        .untilAsserted(
            () -> {
              var cm = extension().get(ConfigMap.class, resource.getMetadata().getName());
              var resources = externalService.listResources();
              assertThat(cm).isNull();
              assertThat(resources).isEmpty();
            });
  }

  private void assertResourcesCreated(
      ExternalStateCustomResource resource, String initialTestData) {
    await()
        .untilAsserted(
            () -> {
              var cm = extension().get(ConfigMap.class, resource.getMetadata().getName());
              var resources = externalService.listResources();
              assertThat(resources).hasSize(1);
              var extRes = externalService.listResources().get(0);
              assertThat(extRes.getData()).isEqualTo(initialTestData);
              assertThat(cm).isNotNull();
              assertThat(cm.getData().get(ID_KEY)).isEqualTo(extRes.getId());
            });
  }

  private ExternalStateCustomResource testResource() {
    var res = new ExternalStateCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());

    res.setSpec(new ExternalStateSpec());
    res.getSpec().setData(INITIAL_TEST_DATA);
    return res;
  }

  abstract LocallyRunOperatorExtension extension();
}

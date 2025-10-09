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
package io.javaoperatorsdk.operator.dependent.multiplemanagedexternaldependenttype;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.dependent.multiplemanageddependentsametype.MultipleManagedDependentResourceSpec;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.support.ExternalServiceMock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Multiple Managed External Dependents of Same Type",
    description =
        """
        Tests managing multiple external (non-Kubernetes) dependent resources of the same type. \
        This demonstrates that operators can manage multiple instances of external resources \
        simultaneously, handling their lifecycle including creation, updates, and deletion.
        """)
class MultipleManagedExternalDependentSameTypeIT {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new MultipleManagedExternalDependentResourceReconciler())
          .build();

  public static final String TEST_RESOURCE_NAME = "test1";
  public static final String DEFAULT_SPEC_VALUE = "val";
  public static final String UPDATED_SPEC_VALUE = "updated-val";

  protected ExternalServiceMock externalServiceMock = ExternalServiceMock.getInstance();

  @Test
  void handlesExternalCrudOperations() {
    extension.create(testResource());
    assertResourceCreatedWithData(DEFAULT_SPEC_VALUE);

    var updatedResource = testResource();
    updatedResource.getSpec().setValue(UPDATED_SPEC_VALUE);
    extension.replace(updatedResource);
    assertResourceCreatedWithData(UPDATED_SPEC_VALUE);

    extension.delete(testResource());
    assertExternalResourceDeleted();
  }

  private void assertExternalResourceDeleted() {
    await()
        .untilAsserted(
            () -> {
              var resources = externalServiceMock.listResources();
              assertThat(resources).hasSize(0);
            });
  }

  private void assertResourceCreatedWithData(String expectedData) {
    await()
        .untilAsserted(
            () -> {
              var resources = externalServiceMock.listResources();
              assertThat(resources).hasSize(2);
              assertThat(resources).allMatch(er -> er.getData().equals(expectedData));
            });
  }

  private MultipleManagedExternalDependentResourceCustomResource testResource() {
    var res = new MultipleManagedExternalDependentResourceCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());

    res.setSpec(new MultipleManagedDependentResourceSpec());
    res.getSpec().setValue(DEFAULT_SPEC_VALUE);
    return res;
  }
}

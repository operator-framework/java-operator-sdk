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
package io.javaoperatorsdk.operator.baseapi.externalstateinstatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.support.ExternalIDGenServiceMock;
import io.javaoperatorsdk.operator.support.ExternalServiceResetExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Manages an external resource while storing its state (the external resource ID) directly in the
 * <b>status</b> of the custom resource, rather than in a separate resource like a ConfigMap. This
 * is only reliable because of the stronger read-after-write consistency for updates: after the
 * external resource is created the reconciler patches the status with the ID, and that patched
 * resource is placed into the cache so the next reconciliation observes the ID and does not create
 * a duplicate external resource.
 */
@Sample(
    tldr = "Managing an External Resource with State Stored in the Status",
    description =
        """
        Demonstrates how to manage an external resource (outside of Kubernetes) while storing its \
        state - the generated external ID - in the status of the custom resource. The reconciler \
        persists the ID with a status patch and relies on the stronger read-after-write \
        consistency for updates so that the next reconciliation observes the stored ID and never \
        creates a duplicate external resource. A fake external service stands in for the managed \
        external system.
        """)
@ExtendWith(ExternalServiceResetExtension.class)
class ExternalStateInStatusIT {

  private static final String TEST_RESOURCE_NAME = "test1";

  public static final String INITIAL_TEST_DATA = "initialTestData";
  public static final String UPDATED_DATA = "updatedData";

  private final ExternalIDGenServiceMock externalService = ExternalIDGenServiceMock.getInstance();

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(ExternalStateInStatusReconciler.class)
          .build();

  @Test
  void reconcilesResourceWithStateStoredInStatus() {
    var resource = operator.create(testResource());
    assertResourceCreated(INITIAL_TEST_DATA);

    resource.getSpec().setData(UPDATED_DATA);
    operator.replace(resource);
    assertResourceCreated(UPDATED_DATA);

    operator.delete(resource);
    assertResourceDeleted();
  }

  private void assertResourceCreated(String expectedData) {
    await()
        .untilAsserted(
            () -> {
              var resources = externalService.listResources();
              // exactly one external resource is created, no duplicates
              assertThat(resources).hasSize(1);
              var extRes = resources.get(0);
              assertThat(extRes.getData()).isEqualTo(expectedData);

              var cr = operator.get(ExternalStateInStatusCustomResource.class, TEST_RESOURCE_NAME);
              assertThat(cr.getStatus()).isNotNull();
              // the external resource state (its ID) is stored in the status
              assertThat(cr.getStatus().getId()).isEqualTo(extRes.getId());
            });
  }

  private void assertResourceDeleted() {
    await().untilAsserted(() -> assertThat(externalService.listResources()).isEmpty());
  }

  private ExternalStateInStatusCustomResource testResource() {
    var res = new ExternalStateInStatusCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    res.setSpec(new ExternalStateInStatusSpec().setData(INITIAL_TEST_DATA));
    return res;
  }
}

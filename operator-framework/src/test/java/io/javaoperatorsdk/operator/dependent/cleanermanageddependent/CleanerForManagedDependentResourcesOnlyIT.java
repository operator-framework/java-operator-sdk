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
package io.javaoperatorsdk.operator.dependent.cleanermanageddependent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class CleanerForManagedDependentResourcesOnlyIT {

  public static final String TEST_RESOURCE_NAME = "cleaner-for-reconciler-test1";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new CleanerForManagedDependentTestReconciler())
          .build();

  @Test
  void addsFinalizerAndCallsCleanupIfCleanerImplemented() {
    var testResource = createTestResource();
    operator.create(testResource);

    await()
        .until(
            () ->
                !operator
                    .get(CleanerForManagedDependentCustomResource.class, TEST_RESOURCE_NAME)
                    .getMetadata()
                    .getFinalizers()
                    .isEmpty());

    operator.delete(testResource);

    await()
        .until(
            () ->
                operator.get(CleanerForManagedDependentCustomResource.class, TEST_RESOURCE_NAME)
                    == null);

    CleanerForManagedDependentTestReconciler reconciler =
        (CleanerForManagedDependentTestReconciler) operator.getFirstReconciler();

    assertThat(reconciler.getNumberOfExecutions()).isEqualTo(1);
    assertThat(ConfigMapDependentResource.getNumberOfCleanupExecutions()).isEqualTo(1);
  }

  private CleanerForManagedDependentCustomResource createTestResource() {
    CleanerForManagedDependentCustomResource cr = new CleanerForManagedDependentCustomResource();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName(TEST_RESOURCE_NAME);
    return cr;
  }
}

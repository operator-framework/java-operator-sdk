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
package io.javaoperatorsdk.operator.baseapi.cleanerforreconciler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class CleanerForReconcilerIT {

  public static final String TEST_RESOURCE_NAME = "cleaner-for-reconciler-test1";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new CleanerForReconcilerTestReconciler())
          .build();

  @Test
  void addsFinalizerAndCallsCleanupIfCleanerImplemented() {
    CleanerForReconcilerTestReconciler reconciler =
        operator.getReconcilerOfType(CleanerForReconcilerTestReconciler.class);
    reconciler.setReScheduleCleanup(false);

    var testResource = createTestResource();
    operator.create(testResource);

    await()
        .until(
            () ->
                !operator
                    .get(CleanerForReconcilerCustomResource.class, TEST_RESOURCE_NAME)
                    .getMetadata()
                    .getFinalizers()
                    .isEmpty());

    operator.delete(testResource);

    await()
        .until(
            () ->
                operator.get(CleanerForReconcilerCustomResource.class, TEST_RESOURCE_NAME) == null);

    assertThat(reconciler.getNumberOfExecutions()).isEqualTo(1);
    assertThat(reconciler.getNumberOfCleanupExecutions()).isEqualTo(1);
  }

  @Test
  void reSchedulesCleanupIfInstructed() {
    CleanerForReconcilerTestReconciler reconciler =
        operator.getReconcilerOfType(CleanerForReconcilerTestReconciler.class);
    reconciler.setReScheduleCleanup(true);

    var testResource = createTestResource();
    operator.create(testResource);

    await()
        .until(
            () ->
                !operator
                    .get(CleanerForReconcilerCustomResource.class, TEST_RESOURCE_NAME)
                    .getMetadata()
                    .getFinalizers()
                    .isEmpty());

    operator.delete(testResource);

    await()
        .untilAsserted(
            () -> assertThat(reconciler.getNumberOfCleanupExecutions()).isGreaterThan(5));

    reconciler.setReScheduleCleanup(false);
    await()
        .until(
            () ->
                operator.get(CleanerForReconcilerCustomResource.class, TEST_RESOURCE_NAME) == null);
  }

  private CleanerForReconcilerCustomResource createTestResource() {
    CleanerForReconcilerCustomResource cr = new CleanerForReconcilerCustomResource();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName(TEST_RESOURCE_NAME);
    return cr;
  }
}

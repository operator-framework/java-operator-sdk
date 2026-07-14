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
package io.javaoperatorsdk.operator.baseapi.resourceoperations;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.baseapi.resourceoperations.SecondaryResourceOperationsReconciler.Operation;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.resourceoperations.SecondaryResourceOperationsReconciler.APPLIED_VALUE;
import static io.javaoperatorsdk.operator.baseapi.resourceoperations.SecondaryResourceOperationsReconciler.CREATE_VALUE;
import static io.javaoperatorsdk.operator.baseapi.resourceoperations.SecondaryResourceOperationsReconciler.DATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for the secondary-resource variants of {@link
 * io.javaoperatorsdk.operator.api.reconciler.ResourceOperations} (the overloads taking an {@code
 * InformerEventSource}). For each operation it asserts:
 *
 * <ul>
 *   <li>the managed {@code ConfigMap} secondary is created/updated to the expected value, and
 *   <li>the write on the secondary is filtered as an own event, so the controller converges and
 *       does not loop on its own secondary write.
 * </ul>
 */
class SecondaryResourceOperationsIT {

  static final String RESOURCE_NAME = "test-resource";

  SecondaryResourceOperationsReconciler reconciler = new SecondaryResourceOperationsReconciler();

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(reconciler).build();

  @Test
  void create() {
    reconciler.setOperation(Operation.CREATE);
    extension.create(testResource());
    awaitConfigMapValue(CREATE_VALUE);
    assertConvergesWithoutLooping(Operation.CREATE);
  }

  @Test
  void serverSideApply() {
    assertAppliedOperation(Operation.SSA);
  }

  @Test
  void update() {
    assertAppliedOperation(Operation.UPDATE);
  }

  @Test
  void jsonPatch() {
    assertAppliedOperation(Operation.JSON_PATCH);
  }

  @Test
  void jsonMergePatch() {
    assertAppliedOperation(Operation.JSON_MERGE_PATCH);
  }

  private void assertAppliedOperation(Operation operation) {
    reconciler.setOperation(operation);
    extension.create(testResource());
    awaitConfigMapValue(APPLIED_VALUE);
    assertConvergesWithoutLooping(operation);
  }

  private void awaitConfigMapValue(String expected) {
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              var cm = extension.get(ConfigMap.class, RESOURCE_NAME);
              assertThat(cm).isNotNull();
              assertThat(cm.getData()).containsEntry(DATA_KEY, expected);
            });
  }

  private void assertConvergesWithoutLooping(Operation operation) {
    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(reconciler.numberOfExecutions.get())
                    .as(
                        "operation %s should converge without looping on its own secondary write",
                        operation)
                    .isLessThanOrEqualTo(4));
  }

  SecondaryResourceOperationsCustomResource testResource() {
    var r = new SecondaryResourceOperationsCustomResource();
    r.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    r.setSpec(new ResourceOperationsSpec().setValue("initial"));
    return r;
  }
}

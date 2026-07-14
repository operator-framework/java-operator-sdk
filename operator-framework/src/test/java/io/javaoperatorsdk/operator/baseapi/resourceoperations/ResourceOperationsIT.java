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

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.baseapi.resourceoperations.ResourceOperationsReconciler.Operation;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.resourceoperations.ResourceOperationsReconciler.SPEC_MARKER;
import static io.javaoperatorsdk.operator.baseapi.resourceoperations.ResourceOperationsReconciler.STATUS_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test that drives every primary update/patch operation exposed by {@link
 * io.javaoperatorsdk.operator.api.reconciler.ResourceOperations} against a real cluster through the
 * reconciler. For each operation it asserts:
 *
 * <ul>
 *   <li>the intended change is actually persisted on the server (spec marker or status value), and
 *   <li>the operation converges: since every operation uses its default matcher and filters its own
 *       event, the controller must not loop on the write it just performed.
 * </ul>
 */
class ResourceOperationsIT {

  static final String RESOURCE_NAME = "test-resource";
  static final String INITIAL_SPEC_VALUE = "initial";

  ResourceOperationsReconciler reconciler = new ResourceOperationsReconciler();

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(reconciler).build();

  @Test
  void serverSideApply() {
    assertSpecOperation(Operation.SSA);
  }

  @Test
  void serverSideApplyStatus() {
    assertStatusOperation(Operation.SSA_STATUS);
  }

  @Test
  void update() {
    assertSpecOperation(Operation.UPDATE);
  }

  @Test
  void updateStatus() {
    assertStatusOperation(Operation.UPDATE_STATUS);
  }

  @Test
  void jsonPatch() {
    assertSpecOperation(Operation.JSON_PATCH);
  }

  @Test
  void jsonPatchStatus() {
    assertStatusOperation(Operation.JSON_PATCH_STATUS);
  }

  @Test
  void jsonMergePatch() {
    assertSpecOperation(Operation.JSON_MERGE_PATCH);
  }

  @Test
  void jsonMergePatchStatus() {
    assertStatusOperation(Operation.JSON_MERGE_PATCH_STATUS);
  }

  private void assertSpecOperation(Operation operation) {
    runOperation(operation);
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(actual().getSpec().getValue())
                    .as("operation %s should persist the spec change", operation)
                    .isEqualTo(SPEC_MARKER));
    assertConvergesWithoutLooping(operation);
  }

  private void assertStatusOperation(Operation operation) {
    runOperation(operation);
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(actual().getStatus()).isNotNull();
              assertThat(actual().getStatus().getValue())
                  .as("operation %s should persist the status value", operation)
                  .isEqualTo(STATUS_VALUE);
            });
    assertConvergesWithoutLooping(operation);
  }

  private void runOperation(Operation operation) {
    reconciler.setOperation(operation);
    extension.create(testResource());
  }

  /**
   * The write must be filtered as an own event and any further reconciliation must match the
   * desired state, so the controller does not loop on its own write.
   */
  private void assertConvergesWithoutLooping(Operation operation) {
    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(reconciler.numberOfExecutions.get())
                    .as("operation %s should converge without triggering an event loop", operation)
                    .isLessThanOrEqualTo(3));
  }

  private ResourceOperationsCustomResource actual() {
    return extension.get(ResourceOperationsCustomResource.class, RESOURCE_NAME);
  }

  ResourceOperationsCustomResource testResource() {
    var r = new ResourceOperationsCustomResource();
    r.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    r.setSpec(new ResourceOperationsSpec().setValue(INITIAL_SPEC_VALUE));
    return r;
  }
}

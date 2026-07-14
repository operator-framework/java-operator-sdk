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
package io.javaoperatorsdk.operator.baseapi.readcacheafterwrite.specchangeduringstatuspatch;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.readcacheafterwrite.specchangeduringstatuspatch.SpecChangeDuringStatusPatchReconciler.STATUS_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Reproduces a concurrent spec change happening while the controller patches its own status. When
 * the reconciler patches the status it opens an event filtering window so it does not re-trigger
 * itself. This test changes the spec on the cluster while that window is open and verifies that the
 * spec change is still reconciled - it must not be silently absorbed together with the controller's
 * own status update.
 */
class SpecChangeDuringStatusPatchIT {

  static final String RESOURCE_NAME = "test-resource";
  static final String SPEC_VALUE = "initial";
  public static final String UPDATED_SPEC_VALUE = "updated-val";

  SpecChangeDuringStatusPatchReconciler reconciler = new SpecChangeDuringStatusPatchReconciler();

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(reconciler).build();

  @RepeatedTest(3)
  void specChangeDuringStatusPatchIsReconciled() throws InterruptedException {
    var res = extension.create(testResource());
    var statusRes = testResource();
    statusRes.getMetadata().setNamespace(res.getMetadata().getNamespace());
    extension
        .getKubernetesClient()
        .resource(statusRes)
        .status()
        .patch(
            new PatchContext.Builder()
                .withForce(true)
                .withFieldManager(
                    SpecChangeDuringStatusPatchReconciler.class.getSimpleName().toLowerCase())
                .withPatchType(PatchType.SERVER_SIDE_APPLY)
                .build());

    // wait until the reconciler is inside its own status patch, holding the filtering window open
    assertThat(reconciler.statusPatchStartedLatch.await(30, TimeUnit.SECONDS))
        .as("reconciler should enter its own status patch operation")
        .isTrue();

    // change the spec on the cluster while the controller's status patch is still in flight
    var current = extension.get(SpecChangeDuringStatusPatchCustomResource.class, RESOURCE_NAME);
    current.getSpec().setValue(UPDATED_SPEC_VALUE);
    extension.replace(current);

    // let the reconciler finish its own status patch
    reconciler.specChangeDoneLatch.countDown();

    // the spec change must be picked up by a fresh reconciliation and not lost with the own update
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(reconciler.numberOfExecutions.get()).isGreaterThanOrEqualTo(2);
              assertThat(reconciler.lastObservedSpecValue.get())
                  .as("a later reconciliation must observe the externally-applied spec change")
                  .isEqualTo(UPDATED_SPEC_VALUE);
            });

    // sanity check: the status the controller set is still present after the concurrent spec change
    var updated = extension.get(SpecChangeDuringStatusPatchCustomResource.class, RESOURCE_NAME);
    assertThat(updated.getSpec().getValue()).isEqualTo(UPDATED_SPEC_VALUE);
    assertThat(updated.getStatus()).isNotNull();
    assertThat(updated.getStatus().getValue()).isEqualTo(STATUS_VALUE);
  }

  SpecChangeDuringStatusPatchCustomResource testResource() {
    var r = new SpecChangeDuringStatusPatchCustomResource();
    r.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    r.setSpec(new SpecChangeDuringStatusPatchSpec().setValue(SPEC_VALUE));
    r.setStatus(new SpecChangeDuringStatusPatchStatus().setValue(STATUS_VALUE));
    return r;
  }
}

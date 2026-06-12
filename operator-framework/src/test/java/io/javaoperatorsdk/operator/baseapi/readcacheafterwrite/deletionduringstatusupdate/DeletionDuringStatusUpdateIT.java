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
package io.javaoperatorsdk.operator.baseapi.readcacheafterwrite.deletionduringstatusupdate;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Regression test for: deletion event dropped when resource is deleted concurrently with a status
 * update.
 */
class DeletionDuringStatusUpdateIT {

  static final String RESOURCE_NAME = "test-resource";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new DeletionDuringStatusUpdateReconciler())
          .build();

  @AfterEach
  void forceCleanup() {
    // If the test failed, remove the finalizer so the resource can be deleted
    var res = extension.get(DeletionDuringStatusUpdateCustomResource.class, RESOURCE_NAME);
    if (res != null) {
      res.getMetadata().setFinalizers(Collections.emptyList());
      extension.replace(res);
      extension.delete(res);
    }
  }

  @Test
  void deletionDuringStatusUpdateTriggersCleanup() throws InterruptedException {
    var reconciler = extension.getReconcilerOfType(DeletionDuringStatusUpdateReconciler.class);

    extension.create(testResource());

    // Wait until the reconciler is inside the update operation (active-update window is open)
    assertThat(reconciler.patchStartedLatch.await(30, TimeUnit.SECONDS))
        .as("reconciler should enter the patch update operation")
        .isTrue();

    // Issue delete — K8s sets deletionTimestamp while the active-update window is open
    extension.delete(testResource());

    // Wait for deletionTimestamp to be confirmed on the resource in K8s
    await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> {
              var res =
                  extension.get(DeletionDuringStatusUpdateCustomResource.class, RESOURCE_NAME);
              return res != null && res.isMarkedForDeletion();
            });

    // Signal the reconciler to proceed with the actual PATCH. K8s will merge deletionTimestamp
    // into the response - the deletion event (lower RV) is now deferred and will be dropped
    // without the fix.
    reconciler.deleteConfirmedLatch.countDown();

    // cleanup() must be called — the deletion must not be silently lost
    assertThat(reconciler.cleanupCalledLatch.await(30, TimeUnit.SECONDS))
        .as("cleanup() must be called after the status update that races with the delete")
        .isTrue();

    // Resource must eventually disappear (finalizer removed)
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(
                        extension.get(
                            DeletionDuringStatusUpdateCustomResource.class, RESOURCE_NAME))
                    .isNull());
  }

  DeletionDuringStatusUpdateCustomResource testResource() {
    var resource = new DeletionDuringStatusUpdateCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    return resource;
  }
}

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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

/**
 * On the first reconciliation the reconciler patches its own status, but keeps the event filtering
 * window open until the test signals that it has changed the spec on the cluster. This reproduces
 * the race where a spec change lands while the controller's own status patch is in flight: the spec
 * change event must still propagate as a fresh reconciliation, it must not be absorbed as if it
 * were our own status update.
 */
@ControllerConfiguration
public class SpecChangeDuringStatusPatchReconciler
    implements Reconciler<SpecChangeDuringStatusPatchCustomResource> {

  static final String STATUS_VALUE = "reconciled";

  final AtomicInteger numberOfExecutions = new AtomicInteger();
  final CountDownLatch statusPatchStartedLatch = new CountDownLatch(1);
  final CountDownLatch specChangeDoneLatch = new CountDownLatch(1);
  final AtomicReference<String> lastObservedSpecValue = new AtomicReference<>();

  @Override
  public UpdateControl<SpecChangeDuringStatusPatchCustomResource> reconcile(
      SpecChangeDuringStatusPatchCustomResource resource,
      Context<SpecChangeDuringStatusPatchCustomResource> context) {
    int execution = numberOfExecutions.incrementAndGet();
    lastObservedSpecValue.set(resource.getSpec().getValue());

    if (execution == 1) {
      resource.setStatus(new SpecChangeDuringStatusPatchStatus().setValue(STATUS_VALUE));
      resource.getMetadata().setResourceVersion(null);
      // Patch our own status, but hold the filtering window open with a hook that lets the test
      // change the spec on the cluster WHILE the status patch is still in flight.
      statusPatchStartedLatch.countDown();
      try {
        if (!specChangeDoneLatch.await(30, TimeUnit.SECONDS)) {
          throw new IllegalStateException("timed out waiting for external spec change");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
      return UpdateControl.patchStatus(resource);
    }
    return UpdateControl.noUpdate();
  }
}

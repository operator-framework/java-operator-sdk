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
package io.javaoperatorsdk.operator.baseapi.statuspatchnonlocking;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class StatusPatchLockingReconciler implements Reconciler<StatusPatchLockingCustomResource> {

  public static final String MESSAGE = "message";
  public static final long WAIT_TIME = 500L;
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<StatusPatchLockingCustomResource> reconcile(
      StatusPatchLockingCustomResource resource, Context<StatusPatchLockingCustomResource> context)
      throws InterruptedException {
    numberOfExecutions.addAndGet(1);
    Thread.sleep(WAIT_TIME);

    if (resource.getStatus() == null) {
      resource.setStatus(new StatusPatchLockingCustomResourceStatus());
    }
    resource.getStatus().setMessage(resource.getSpec().isMessageInStatus() ? MESSAGE : null);
    resource.getStatus().setValue(resource.getStatus().getValue() + 1);
    return UpdateControl.patchStatus(resource);
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}

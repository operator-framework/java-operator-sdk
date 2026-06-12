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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class DeletionDuringStatusUpdateReconciler
    implements Reconciler<DeletionDuringStatusUpdateCustomResource>,
        Cleaner<DeletionDuringStatusUpdateCustomResource> {

  final CountDownLatch patchStartedLatch = new CountDownLatch(1);
  final CountDownLatch deleteConfirmedLatch = new CountDownLatch(1);
  final CountDownLatch cleanupCalledLatch = new CountDownLatch(1);

  @Override
  public UpdateControl<DeletionDuringStatusUpdateCustomResource> reconcile(
      DeletionDuringStatusUpdateCustomResource resource,
      Context<DeletionDuringStatusUpdateCustomResource> context)
      throws InterruptedException {
    if (resource.isMarkedForDeletion()) {
      return UpdateControl.noUpdate();
    }

    var status = new DeletionDuringStatusUpdateStatus();
    status.setReady(true);
    resource.setStatus(status);

    context
        .resourceOperations()
        .resourcePatch(
            resource,
            r -> {
              patchStartedLatch.countDown();
              try {
                if (!deleteConfirmedLatch.await(30, TimeUnit.SECONDS)) {
                  throw new RuntimeException("Timed out waiting for delete confirmation");
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
              }
              r.getMetadata().setResourceVersion(null);
              return context.getClient().resource(r).patchStatus();
            },
            context.eventSourceRetriever().getControllerEventSource());

    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(
      DeletionDuringStatusUpdateCustomResource resource,
      Context<DeletionDuringStatusUpdateCustomResource> context) {
    cleanupCalledLatch.countDown();
    return DeleteControl.defaultDelete();
  }
}

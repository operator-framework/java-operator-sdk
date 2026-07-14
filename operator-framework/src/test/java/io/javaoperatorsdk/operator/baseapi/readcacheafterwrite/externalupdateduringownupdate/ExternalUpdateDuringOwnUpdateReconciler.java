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
package io.javaoperatorsdk.operator.baseapi.readcacheafterwrite.externalupdateduringownupdate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(generationAwareEventProcessing = false)
public class ExternalUpdateDuringOwnUpdateReconciler
    implements Reconciler<ExternalUpdateDuringOwnUpdateCustomResource> {

  static final String EXTERNAL_LABEL_KEY = "externally-set";
  static final String EXTERNAL_LABEL_VALUE = "yes";
  static final String STATUS_VALUE = "ready";

  final AtomicInteger numberOfExecutions = new AtomicInteger();
  final CountDownLatch updateStartedLatch = new CountDownLatch(1);
  final CountDownLatch externalUpdateDoneLatch = new CountDownLatch(1);
  final AtomicBoolean externalLabelSeenInLaterReconciliation = new AtomicBoolean();

  @Override
  public UpdateControl<ExternalUpdateDuringOwnUpdateCustomResource> reconcile(
      ExternalUpdateDuringOwnUpdateCustomResource resource,
      Context<ExternalUpdateDuringOwnUpdateCustomResource> context) {
    int execution = numberOfExecutions.incrementAndGet();

    if (execution == 1) {
      var status = new ExternalUpdateDuringOwnUpdateStatus().setValue(STATUS_VALUE);
      resource.setStatus(status);

      // wrap our own status update in resourcePatch with a hook that lets the test
      // perform an external metadata update WHILE our filter window is still open.
      context
          .resourceOperations()
          .resourcePatch(
              resource,
              r -> {
                updateStartedLatch.countDown();
                try {
                  if (!externalUpdateDoneLatch.await(30, TimeUnit.SECONDS)) {
                    throw new RuntimeException("timed out waiting for external update");
                  }
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new RuntimeException(e);
                }
                // server-side state moved due to the external label change; drop our stale rv
                r.getMetadata().setResourceVersion(null);
                return context.getClient().resource(r).patchStatus();
              },
              context.eventSourceRetriever().getControllerEventSource());
    } else {
      var labels = resource.getMetadata().getLabels();
      if (labels != null && EXTERNAL_LABEL_VALUE.equals(labels.get(EXTERNAL_LABEL_KEY))) {
        externalLabelSeenInLaterReconciliation.set(true);
      }
    }
    return UpdateControl.noUpdate();
  }
}

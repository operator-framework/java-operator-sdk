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
package io.javaoperatorsdk.operator.baseapi.readcacheafterwrite.ownssastatusupdate;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

/**
 * Updates its own status via {@link
 * io.javaoperatorsdk.operator.api.reconciler.ResourceOperations#serverSideApplyPrimaryStatus(
 * io.fabric8.kubernetes.api.model.HasMetadata)} instead of returning an {@link UpdateControl}. The
 * status is server-side applied through the controller's own event source, so the resulting own
 * event must be filtered and must not trigger a reconciliation loop. A later external update (a
 * label change) must still be observed by a fresh reconciliation.
 */
@ControllerConfiguration(generationAwareEventProcessing = false)
public class OwnSsaStatusUpdateReconciler implements Reconciler<OwnSsaStatusUpdateCustomResource> {

  static final String STATUS_VALUE = "ready";
  static final String EXTERNAL_LABEL_KEY = "externally-set";
  static final String EXTERNAL_LABEL_VALUE = "yes";

  final AtomicInteger numberOfExecutions = new AtomicInteger();
  final AtomicBoolean externalLabelSeenInLaterReconciliation = new AtomicBoolean();

  @Override
  public UpdateControl<OwnSsaStatusUpdateCustomResource> reconcile(
      OwnSsaStatusUpdateCustomResource resource,
      Context<OwnSsaStatusUpdateCustomResource> context) {
    numberOfExecutions.incrementAndGet();

    var labels = resource.getMetadata().getLabels();
    if (labels != null && EXTERNAL_LABEL_VALUE.equals(labels.get(EXTERNAL_LABEL_KEY))) {
      externalLabelSeenInLaterReconciliation.set(true);
    }

    // Only apply the status when it is not already set - the SSA status matcher makes repeated
    // applies no-ops anyway, but this keeps the intent explicit and the reconciliation idempotent.
    if (resource.getStatus() == null || !STATUS_VALUE.equals(resource.getStatus().getValue())) {
      resource.setStatus(new OwnSsaStatusUpdateStatus().setValue(STATUS_VALUE));
      // SSA does not use optimistic locking
      resource.getMetadata().setResourceVersion(null);
      context.resourceOperations().serverSideApplyPrimaryStatus(resource);
    }

    return UpdateControl.noUpdate();
  }
}

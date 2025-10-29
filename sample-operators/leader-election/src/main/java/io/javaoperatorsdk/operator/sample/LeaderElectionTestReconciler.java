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
package io.javaoperatorsdk.operator.sample;

import java.time.Duration;
import java.util.ArrayList;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.sample.v1.LeaderElection;
import io.javaoperatorsdk.operator.sample.v1.LeaderElectionStatus;

@ControllerConfiguration()
public class LeaderElectionTestReconciler implements Reconciler<LeaderElection> {

  private final String reconcilerName;

  public LeaderElectionTestReconciler(String reconcilerName) {
    this.reconcilerName = reconcilerName;
  }

  @Override
  public UpdateControl<LeaderElection> reconcile(
      LeaderElection resource, Context<LeaderElection> context) {

    if (resource.getStatus() == null) {
      resource.setStatus(new LeaderElectionStatus());
    }
    if (resource.getStatus().getReconciledBy() == null) {
      resource.getStatus().setReconciledBy(new ArrayList<>());
    }

    resource.getStatus().getReconciledBy().add(reconcilerName);
    // update status is with optimistic locking
    return UpdateControl.patchStatus(resource).rescheduleAfter(Duration.ofSeconds(1));
  }
}

package io.javaoperatorsdk.operator.sample;

import java.time.Duration;
import java.util.ArrayList;
import javaoperatorsdk.sample.v1.LeaderElection;
import javaoperatorsdk.sample.v1.LeaderElectionStatus;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration()
public class LeaderElectionTestReconciler
    implements Reconciler<LeaderElection> {

  private final String reconcilerName;

  public LeaderElectionTestReconciler(String reconcilerName) {
    this.reconcilerName = reconcilerName;
  }

  @Override
  public UpdateControl<LeaderElection> reconcile(
      LeaderElection resource,
      Context<LeaderElection> context) {

    if (resource.getStatus() == null) {
      resource.setStatus(new LeaderElectionStatus());
    }
    if (resource.getStatus().getReconciledBy() == null) {
      resource.getStatus().setReconciledBy(new ArrayList<>());
    }

    resource.getStatus().getReconciledBy().add(reconcilerName);
    // update status is with optimistic locking
    return UpdateControl.updateStatus(resource).rescheduleAfter(Duration.ofSeconds(1));
  }

}

package io.javaoperatorsdk.operator.sample;

import java.time.Duration;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration()
public class LeaderElectionTestReconciler
    implements Reconciler<LeaderElectionTestCustomResource> {

  private final String reconcilerName;

  public LeaderElectionTestReconciler(String reconcilerName) {
    this.reconcilerName = reconcilerName;
  }

  @Override
  public UpdateControl<LeaderElectionTestCustomResource> reconcile(
      LeaderElectionTestCustomResource resource,
      Context<LeaderElectionTestCustomResource> context) {

    if (resource.getStatus() == null) {
      resource.setStatus(new LeaderElectionTestStatus());
    }

    resource.getStatus().getReconciledBy().add(reconcilerName);
    // update status is with optimistic locking
    return UpdateControl.updateStatus(resource).rescheduleAfter(Duration.ofSeconds(1));
  }

}

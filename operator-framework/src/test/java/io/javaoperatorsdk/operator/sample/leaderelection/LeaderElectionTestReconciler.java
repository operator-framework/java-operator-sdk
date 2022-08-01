package io.javaoperatorsdk.operator.sample.leaderelection;

import java.time.Duration;

import io.javaoperatorsdk.operator.api.reconciler.*;

@ControllerConfiguration()
public class LeaderElectionTestReconciler
    implements Reconciler<LeaderElectionTestCustomResource> {

  private String reconcilerName;

  public LeaderElectionTestReconciler(String reconcilerName) {
    this.reconcilerName = reconcilerName;
  }

  @Override
  public UpdateControl<LeaderElectionTestCustomResource> reconcile(
      LeaderElectionTestCustomResource resource,
      Context<LeaderElectionTestCustomResource> context) {

    if (resource.getStatus() == null) {
      resource.setStatus(new LeaderElectionTestCustomResourceStatus());
    }

    resource.getStatus().getReconciledBy().add(reconcilerName);
    return UpdateControl.patchStatus(resource).rescheduleAfter(Duration.ofSeconds(200));
  }

}

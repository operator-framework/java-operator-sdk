package io.javaoperatorsdk.operator.sample;

import java.time.Duration;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration()
public class LeaderElectionTestReconciler
    implements Reconciler<LeaderElectionTest> {


  private final String reconcilerName;

  public LeaderElectionTestReconciler(String reconcilerName) {
    this.reconcilerName = reconcilerName;
  }

  @Override
  public UpdateControl<LeaderElectionTest> reconcile(
      LeaderElectionTest resource,
      Context<LeaderElectionTest> context) {

    if (resource.getStatus() == null) {
      resource.setStatus(new LeaderElectionTestStatus());
    }

    resource.getStatus().getReconciledBy().add(reconcilerName);

    return UpdateControl.patchStatus(resource).rescheduleAfter(Duration.ofSeconds(1));
  }

}

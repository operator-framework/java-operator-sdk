package io.javaoperatorsdk.operator.baseapi.updatestatusincleanupandreschedule;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class UpdateStatusInCleanupAndRescheduleReconciler
    implements Reconciler<UpdateStatusInCleanupAndRescheduleCustomResource>,
        Cleaner<UpdateStatusInCleanupAndRescheduleCustomResource> {

  public static final Integer DELAY = 150;

  private LocalTime lastCleanupExecution;

  public Boolean rescheduleDelayWorked;

  @Override
  public UpdateControl<UpdateStatusInCleanupAndRescheduleCustomResource> reconcile(
      UpdateStatusInCleanupAndRescheduleCustomResource resource,
      Context<UpdateStatusInCleanupAndRescheduleCustomResource> context) {

    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(
      UpdateStatusInCleanupAndRescheduleCustomResource resource,
      Context<UpdateStatusInCleanupAndRescheduleCustomResource> context) {

    var status = resource.getStatus();
    if (status == null) {
      resource.setStatus(new UpdateStatusInCleanupAndRescheduleCustomStatus());
      resource.getStatus().setCleanupAttempt(1);
      lastCleanupExecution = LocalTime.now();
    } else {
      var currentAttempt = resource.getStatus().getCleanupAttempt();
      resource.getStatus().setCleanupAttempt(currentAttempt + 1);
      if (!Boolean.FALSE.equals(rescheduleDelayWorked)) {
        var diff = ChronoUnit.MILLIS.between(lastCleanupExecution, LocalTime.now());
        rescheduleDelayWorked = diff >= DELAY;
      }
    }
    context.getClient().resource(resource).updateStatus();

    if (resource.getStatus().getCleanupAttempt() > 5) {
      return DeleteControl.defaultDelete();
    } else {
      return DeleteControl.noFinalizerRemoval().rescheduleAfter(DELAY);
    }
  }

  public Boolean getRescheduleDelayWorked() {
    return rescheduleDelayWorked;
  }
}

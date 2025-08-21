package io.javaoperatorsdk.operator.baseapi.alleventmode.cleaner;

import io.javaoperatorsdk.operator.api.config.ControllerMode;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.baseapi.alleventmode.AbstractAllEventReconciler;

@ControllerConfiguration(mode = ControllerMode.RECONCILE_ALL_EVENT)
public class AllEventCleanerReconciler extends AbstractAllEventReconciler
    implements Reconciler<AllEventCleanerCustomResource>, Cleaner<AllEventCleanerCustomResource> {

  @Override
  public UpdateControl<AllEventCleanerCustomResource> reconcile(
      AllEventCleanerCustomResource resource, Context<AllEventCleanerCustomResource> context) {

    increaseEventCount();
    if (!resource.isMarkedForDeletion()) {
      setResourceEvent(true);
    }

    if (!resource.hasFinalizer(FINALIZER)) {
      resource.addFinalizer(FINALIZER);
      context.getClient().resource(resource).update();
      return UpdateControl.noUpdate();
    }

    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(
      AllEventCleanerCustomResource resource, Context<AllEventCleanerCustomResource> context)
      throws Exception {

    increaseEventCount();
    if (resource.isMarkedForDeletion() && !context.isDeleteEventPresent()) {
      setEventOnMarkedForDeletion(true);
      if (resource.hasFinalizer(FINALIZER)) {
        resource.removeFinalizer(FINALIZER);
        context.getClient().resource(resource).update();
      }
    }

    if (context.isDeleteEventPresent()) {
      setDeleteEvent(true);
    }
    // todo handle this document
    return DeleteControl.defaultDelete();
  }
}

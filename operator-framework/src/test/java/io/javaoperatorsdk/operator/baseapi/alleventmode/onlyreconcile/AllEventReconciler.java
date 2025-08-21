package io.javaoperatorsdk.operator.baseapi.alleventmode.onlyreconcile;

import io.javaoperatorsdk.operator.api.config.ControllerMode;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.baseapi.alleventmode.AbstractAllEventReconciler;

@ControllerConfiguration(mode = ControllerMode.RECONCILE_ALL_EVENT)
public class AllEventReconciler extends AbstractAllEventReconciler
    implements Reconciler<AllEventCustomResource> {

  @Override
  public UpdateControl<AllEventCustomResource> reconcile(
      AllEventCustomResource resource, Context<AllEventCustomResource> context) {

    increaseEventCount();

    if (!resource.isMarkedForDeletion()) {
      setResourceEventPresent(true);
    }

    if (getUseFinalizer() && !resource.hasFinalizer(FINALIZER)) {
      resource.addFinalizer(FINALIZER);
      context.getClient().resource(resource).update();
      return UpdateControl.noUpdate();
    }

    if (resource.isMarkedForDeletion() && !context.isDeleteEventPresent()) {
      setEventOnMarkedForDeletion(true);
      if (getUseFinalizer() && resource.hasFinalizer(FINALIZER)) {
        resource.removeFinalizer(FINALIZER);
        context.getClient().resource(resource).update();
      }
    }

    if (context.isDeleteEventPresent()
        && isFirstDeleteEvent()
        && isThrowExceptionOnFirstDeleteEvent()) {
      isFirstDeleteEvent = false;
      throw new RuntimeException("On purpose exception");
    }

    if (context.isDeleteEventPresent()) {
      setDeleteEventPresent(true);
    }

    return UpdateControl.noUpdate();
  }
}

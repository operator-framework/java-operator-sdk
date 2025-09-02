package io.javaoperatorsdk.operator.baseapi.alleventmode.onlyreconcile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.config.ControllerMode;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.FinalizerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.baseapi.alleventmode.AbstractAllEventReconciler;

@ControllerConfiguration(
    mode = ControllerMode.RECONCILE_ALL_EVENT,
    generationAwareEventProcessing = false)
public class AllEventReconciler extends AbstractAllEventReconciler
    implements Reconciler<AllEventCustomResource> {

  private static final Logger log = LoggerFactory.getLogger(AllEventReconciler.class);

  @Override
  public UpdateControl<AllEventCustomResource> reconcile(
      AllEventCustomResource primary, Context<AllEventCustomResource> context)
      throws InterruptedException {
    log.info("Reconciling");
    increaseEventCount();

    if (!primary.isMarkedForDeletion()) {
      setResourceEventPresent(true);
    }

    if (!primary.isMarkedForDeletion() && getUseFinalizer() && !primary.hasFinalizer(FINALIZER)) {
      log.info("Adding finalizer");
      FinalizerUtils.patchFinalizer(primary, FINALIZER, context);
      return UpdateControl.noUpdate();
    }

    if (waitAfterFirstRetry
        && context.getRetryInfo().isPresent()
        && context.getRetryInfo().orElseThrow().getAttemptCount() == 1) {
      waiting = true;
      while (!continuerOnRetryWait) {
        Thread.sleep(50);
      }
      waiting = false;
    }

    if (throwExceptionIfNoAnnotation
        && !primary.getMetadata().getAnnotations().containsKey(NO_MORE_EXCEPTION_ANNOTATION_KEY)) {
      throw new RuntimeException("On purpose exception for missing annotation");
    }

    if (primary.isMarkedForDeletion() && !context.isDeleteEventPresent()) {
      setEventOnMarkedForDeletion(true);
      if (getUseFinalizer() && primary.hasFinalizer(FINALIZER)) {
        log.info("Removing finalizer");
        FinalizerUtils.removeFinalizer(primary, FINALIZER, context);
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
    log.info("Reconciliation finished");
    return UpdateControl.noUpdate();
  }
}

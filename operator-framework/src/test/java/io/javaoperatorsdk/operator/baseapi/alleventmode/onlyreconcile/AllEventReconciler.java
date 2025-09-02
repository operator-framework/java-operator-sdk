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

  private volatile boolean throwExceptionOnFirstDeleteEvent = false;
  private volatile boolean throwExceptionIfNoAnnotation = false;

  private volatile boolean waitAfterFirstRetry = false;
  private volatile boolean continuerOnRetryWait = false;
  private volatile boolean waiting = false;

  private volatile boolean isFirstDeleteEvent = true;

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

  public boolean isFirstDeleteEvent() {
    return isFirstDeleteEvent;
  }

  public void setFirstDeleteEvent(boolean firstDeleteEvent) {
    isFirstDeleteEvent = firstDeleteEvent;
  }

  public boolean isThrowExceptionOnFirstDeleteEvent() {
    return throwExceptionOnFirstDeleteEvent;
  }

  public void setThrowExceptionOnFirstDeleteEvent(boolean throwExceptionOnFirstDeleteEvent) {
    this.throwExceptionOnFirstDeleteEvent = throwExceptionOnFirstDeleteEvent;
  }

  public boolean isThrowExceptionIfNoAnnotation() {
    return throwExceptionIfNoAnnotation;
  }

  public void setThrowExceptionIfNoAnnotation(boolean throwExceptionIfNoAnnotation) {
    this.throwExceptionIfNoAnnotation = throwExceptionIfNoAnnotation;
  }

  public boolean isWaitAfterFirstRetry() {
    return waitAfterFirstRetry;
  }

  public void setWaitAfterFirstRetry(boolean waitAfterFirstRetry) {
    this.waitAfterFirstRetry = waitAfterFirstRetry;
  }

  public boolean isContinuerOnRetryWait() {
    return continuerOnRetryWait;
  }

  public void setContinuerOnRetryWait(boolean continuerOnRetryWait) {
    this.continuerOnRetryWait = continuerOnRetryWait;
  }

  public boolean isWaiting() {
    return waiting;
  }

  public void setWaiting(boolean waiting) {
    this.waiting = waiting;
  }
}

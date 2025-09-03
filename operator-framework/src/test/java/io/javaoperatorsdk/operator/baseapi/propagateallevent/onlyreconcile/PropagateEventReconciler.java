package io.javaoperatorsdk.operator.baseapi.propagateallevent.onlyreconcile;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.FinalizerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(
    propagateAllEventToReconciler = true,
    generationAwareEventProcessing = false)
public class PropagateEventReconciler implements Reconciler<PropagateAllEventCustomResource> {

  private static final Logger log = LoggerFactory.getLogger(PropagateEventReconciler.class);

  private volatile boolean throwExceptionOnFirstDeleteEvent = false;
  private volatile boolean throwExceptionIfNoAnnotation = false;

  private volatile boolean waitAfterFirstRetry = false;
  private volatile boolean continuerOnRetryWait = false;
  private volatile boolean waiting = false;

  private volatile boolean isFirstDeleteEvent = true;

  public static final String FINALIZER = "all.event.mode/finalizer";
  public static final String ADDITIONAL_FINALIZER = "all.event.mode/finalizer2";
  public static final String NO_MORE_EXCEPTION_ANNOTATION_KEY = "no.more.exception";

  protected volatile boolean useFinalizer = true;

  private final AtomicInteger eventCounter = new AtomicInteger(0);

  private boolean deleteEventPresent = false;
  private boolean eventOnMarkedForDeletion = false;
  private boolean resourceEventPresent = false;

  @Override
  public UpdateControl<PropagateAllEventCustomResource> reconcile(
      PropagateAllEventCustomResource primary, Context<PropagateAllEventCustomResource> context)
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

  public int getEventCount() {
    return eventCounter.get();
  }

  public void increaseEventCount() {
    eventCounter.incrementAndGet();
  }

  public boolean getUseFinalizer() {
    return useFinalizer;
  }

  public void setUseFinalizer(boolean useFinalizer) {
    this.useFinalizer = useFinalizer;
  }

  public boolean isDeleteEventPresent() {
    return deleteEventPresent;
  }

  public void setDeleteEventPresent(boolean deleteEventPresent) {
    this.deleteEventPresent = deleteEventPresent;
  }

  public boolean isEventOnMarkedForDeletion() {
    return eventOnMarkedForDeletion;
  }

  public void setEventOnMarkedForDeletion(boolean eventOnMarkedForDeletion) {
    this.eventOnMarkedForDeletion = eventOnMarkedForDeletion;
  }

  public boolean isResourceEventPresent() {
    return resourceEventPresent;
  }

  public void setResourceEventPresent(boolean resourceEventPresent) {
    this.resourceEventPresent = resourceEventPresent;
  }
}

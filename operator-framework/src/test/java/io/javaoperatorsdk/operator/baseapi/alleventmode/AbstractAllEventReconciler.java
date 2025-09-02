package io.javaoperatorsdk.operator.baseapi.alleventmode;

import java.util.concurrent.atomic.AtomicInteger;

public class AbstractAllEventReconciler {

  public static final String FINALIZER = "all.event.mode/finalizer";
  public static final String ADDITIONAL_FINALIZER = "all.event.mode/finalizer2";
  public static final String NO_MORE_EXCEPTION_ANNOTATION_KEY = "no.more.exception";

  protected volatile boolean useFinalizer = true;
  protected volatile boolean throwExceptionOnFirstDeleteEvent = false;
  protected volatile boolean throwExceptionIfNoAnnotation = false;

  protected volatile boolean waitAfterFirstRetry = false;
  protected volatile boolean continuerOnRetryWait = false;
  protected volatile boolean waiting = false;

  protected volatile boolean isFirstDeleteEvent = true;

  private boolean resourceEventPresent = false;
  private boolean deleteEventPresent = false;
  private boolean eventOnMarkedForDeletion = false;

  private final AtomicInteger eventCounter = new AtomicInteger(0);

  public boolean isResourceEventPresent() {
    return resourceEventPresent;
  }

  public void setResourceEventPresent(boolean resourceEventPresent) {
    this.resourceEventPresent = resourceEventPresent;
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

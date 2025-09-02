package io.javaoperatorsdk.operator.baseapi.alleventmode;

import java.util.concurrent.atomic.AtomicInteger;

public class AbstractAllEventReconciler {

  public static final String FINALIZER = "all.event.mode/finalizer";
  public static final String ADDITIONAL_FINALIZER = "all.event.mode/finalizer2";
  public static final String NO_MORE_EXCEPTION_ANNOTATION_KEY = "no.more.exception";

  protected volatile boolean useFinalizer = true;

  private final AtomicInteger eventCounter = new AtomicInteger(0);

  private boolean deleteEventPresent = false;
  private boolean eventOnMarkedForDeletion = false;
  private boolean resourceEventPresent = false;

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

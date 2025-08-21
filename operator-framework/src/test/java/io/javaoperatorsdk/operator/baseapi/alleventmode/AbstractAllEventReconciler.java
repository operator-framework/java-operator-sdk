package io.javaoperatorsdk.operator.baseapi.alleventmode;

import java.util.concurrent.atomic.AtomicInteger;

public class AbstractAllEventReconciler {

  public static final String FINALIZER = "all.event.mode/finalizer";
  public static final String ADDITIONAL_FINALIZER = "all.event.mode/finalizer2";

  private boolean resourceEvent = false;
  private boolean deleteEvent = false;
  private boolean eventOnMarkedForDeletion = false;
  private AtomicInteger eventCounter = new AtomicInteger(0);

  public boolean isResourceEvent() {
    return resourceEvent;
  }

  public void setResourceEvent(boolean resourceEvent) {
    this.resourceEvent = resourceEvent;
  }

  public boolean isDeleteEvent() {
    return deleteEvent;
  }

  public void setDeleteEvent(boolean deleteEvent) {
    this.deleteEvent = deleteEvent;
  }

  public boolean isEventOnMarkedForDeletion() {
    return eventOnMarkedForDeletion;
  }

  public void setEventOnMarkedForDeletion(boolean eventOnMarkedForDeletion) {
    this.eventOnMarkedForDeletion = eventOnMarkedForDeletion;
  }

  public int getEventCounter() {
    return eventCounter.get();
  }

  public void increaseEventCount() {
    eventCounter.incrementAndGet();
  }
}

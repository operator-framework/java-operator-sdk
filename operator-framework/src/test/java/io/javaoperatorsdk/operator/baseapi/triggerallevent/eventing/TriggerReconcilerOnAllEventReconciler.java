/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.baseapi.triggerallevent.eventing;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ReconcileUtils;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(
    triggerReconcilerOnAllEvents = true,
    generationAwareEventProcessing = false)
public class TriggerReconcilerOnAllEventReconciler
    implements Reconciler<TriggerReconcilerOnAllEventCustomResource> {

  public static final String FINALIZER = "all.event.mode/finalizer";
  public static final String ADDITIONAL_FINALIZER = "all.event.mode/finalizer2";
  public static final String NO_MORE_EXCEPTION_ANNOTATION_KEY = "no.more.exception";

  private static final Logger log =
      LoggerFactory.getLogger(TriggerReconcilerOnAllEventReconciler.class);

  // control flags to throw exceptions in certain situations
  private volatile boolean throwExceptionOnFirstDeleteEvent = false;
  private volatile boolean throwExceptionIfNoAnnotation = false;

  // flags for managing wait within reconciliation,
  // so we can send an update while reconciliation is in progress
  private volatile boolean waitAfterFirstRetry = false;
  private volatile boolean continuerOnRetryWait = false;
  private volatile boolean waiting = false;

  // control flag to throw an exception on first delete event
  private volatile boolean isFirstDeleteEvent = true;
  // control if the reconciler should add / remove the finalizer
  private volatile boolean useFinalizer = true;

  // flags to flip if reconciled primary resource in certain state
  private boolean deleteEventPresent = false;
  private boolean eventOnMarkedForDeletion = false;
  private boolean resourceEventPresent = false;
  // counter for how many times the reconciler has been called
  private final AtomicInteger reconciliationCounter = new AtomicInteger(0);

  @Override
  public UpdateControl<TriggerReconcilerOnAllEventCustomResource> reconcile(
      TriggerReconcilerOnAllEventCustomResource primary,
      Context<TriggerReconcilerOnAllEventCustomResource> context)
      throws InterruptedException {
    log.info("Reconciling");
    increaseEventCount();

    if (!primary.isMarkedForDeletion()) {
      setResourceEventPresent(true);
    }

    if (!primary.isMarkedForDeletion() && getUseFinalizer() && !primary.hasFinalizer(FINALIZER)) {
      log.info("Adding finalizer");
      ReconcileUtils.addFinalizer(context, FINALIZER);
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

    if (primary.isMarkedForDeletion() && !context.isPrimaryResourceDeleted()) {
      setEventOnMarkedForDeletion(true);
      if (getUseFinalizer() && primary.hasFinalizer(FINALIZER)) {
        log.info("Removing finalizer");
        ReconcileUtils.removeFinalizer(context, FINALIZER);
      }
    }

    if (context.isPrimaryResourceDeleted()
        && isFirstDeleteEvent()
        && isThrowExceptionOnFirstDeleteEvent()) {
      isFirstDeleteEvent = false;
      throw new RuntimeException("On purpose exception");
    }

    if (context.isPrimaryResourceDeleted()) {
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
    return reconciliationCounter.get();
  }

  public void increaseEventCount() {
    reconciliationCounter.incrementAndGet();
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

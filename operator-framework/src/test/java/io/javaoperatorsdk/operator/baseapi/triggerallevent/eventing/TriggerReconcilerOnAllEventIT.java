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

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;

import static io.javaoperatorsdk.operator.baseapi.triggerallevent.eventing.TriggerReconcilerOnAllEventReconciler.ADDITIONAL_FINALIZER;
import static io.javaoperatorsdk.operator.baseapi.triggerallevent.eventing.TriggerReconcilerOnAllEventReconciler.FINALIZER;
import static io.javaoperatorsdk.operator.baseapi.triggerallevent.eventing.TriggerReconcilerOnAllEventReconciler.NO_MORE_EXCEPTION_ANNOTATION_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * This is quite a technical test to check if we receive events properly for various lifecycle
 * events, and situations in the reconciler.
 */
public class TriggerReconcilerOnAllEventIT {

  public static final String TEST = "test1";
  public static final int MAX_RETRY_ATTEMPTS = 2;

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(
              new TriggerReconcilerOnAllEventReconciler(),
              o ->
                  o.withRetry(
                      new GenericRetry()
                          .setInitialInterval(800)
                          .setMaxAttempts(MAX_RETRY_ATTEMPTS)
                          .setIntervalMultiplier(1)))
          .build();

  @Test
  void eventsPresent() {
    var reconciler = extension.getReconcilerOfType(TriggerReconcilerOnAllEventReconciler.class);
    extension.serverSideApply(testResource());
    await()
        .untilAsserted(
            () -> {
              assertThat(reconciler.isResourceEventPresent()).isTrue();
              assertThat(getResource().hasFinalizer(FINALIZER)).isTrue();
            });

    extension.delete(getResource());

    await()
        .untilAsserted(
            () -> {
              var r = getResource();
              assertThat(r).isNull();
              // check if reconciler also triggered when delete event received
              assertThat(reconciler.isDeleteEventPresent()).isTrue();
              // also when it was marked for deletion
              assertThat(reconciler.isEventOnMarkedForDeletion()).isTrue();
            });
  }

  @Test
  void deleteEventPresentWithoutFinalizer() {
    var reconciler = extension.getReconcilerOfType(TriggerReconcilerOnAllEventReconciler.class);
    reconciler.setUseFinalizer(false);
    extension.serverSideApply(testResource());

    await().untilAsserted(() -> assertThat(reconciler.isResourceEventPresent()).isTrue());

    extension.delete(getResource());

    await()
        .untilAsserted(
            () -> {
              var r = getResource();
              assertThat(r).isNull();
              assertThat(reconciler.isDeleteEventPresent()).isTrue();
            });
  }

  @Test
  void retriesExceptionOnDeleteEvent() {
    var reconciler = extension.getReconcilerOfType(TriggerReconcilerOnAllEventReconciler.class);
    reconciler.setUseFinalizer(false);
    reconciler.setThrowExceptionOnFirstDeleteEvent(true);

    extension.serverSideApply(testResource());

    await().untilAsserted(() -> assertThat(reconciler.isResourceEventPresent()).isTrue());

    extension.delete(getResource());

    await()
        .untilAsserted(
            () -> {
              var r = getResource();
              assertThat(r).isNull();
              assertThat(reconciler.isDeleteEventPresent()).isTrue();
            });
  }

  /**
   * Checks if we receive event even after our finalizer is removed but there is an additional
   * finalizer
   */
  @Test
  void additionalFinalizer() {
    var reconciler = extension.getReconcilerOfType(TriggerReconcilerOnAllEventReconciler.class);
    reconciler.setUseFinalizer(true);
    var res = testResource();
    res.addFinalizer(ADDITIONAL_FINALIZER);

    extension.create(res);

    extension.delete(getResource());

    await()
        .untilAsserted(
            () -> {
              var r = getResource();
              assertThat(r).isNotNull();
              assertThat(r.getMetadata().getFinalizers()).containsExactly(ADDITIONAL_FINALIZER);
            });
    var eventCount = reconciler.getEventCount();

    res = getResource();
    res.removeFinalizer(ADDITIONAL_FINALIZER);
    extension.update(res);

    await()
        .untilAsserted(
            () -> {
              var r = getResource();
              assertThat(r).isNull();
              assertThat(reconciler.getEventCount()).isEqualTo(eventCount + 1);
            });
  }

  // tests the situation where we received a delete event, but there is an exception during
  // reconiliation
  // what is retried, but during retry we receive an additional event, that should instantly
  // trigger the reconciliation again when current finished.
  @Test
  void additionalEventDuringRetryOnDeleteEvent() {

    var reconciler = extension.getReconcilerOfType(TriggerReconcilerOnAllEventReconciler.class);
    reconciler.setThrowExceptionIfNoAnnotation(true);
    reconciler.setWaitAfterFirstRetry(true);
    var res = testResource();
    res.addFinalizer(ADDITIONAL_FINALIZER);
    extension.create(res);
    extension.delete(getResource());

    await()
        .pollDelay(Duration.ofMillis(30))
        .untilAsserted(
            () -> {
              assertThat(reconciler.getEventCount()).isGreaterThan(2);
            });
    var eventCount = reconciler.getEventCount();

    await()
        .untilAsserted(
            () -> {
              assertThat(reconciler.isWaiting());
            });

    // trigger reconciliation while waiting in reconciler
    res = getResource();
    res.getMetadata().getAnnotations().put("my-annotation", "true");
    extension.update(res);
    // continue reconciliation
    reconciler.setContinuerOnRetryWait(true);

    await()
        .pollDelay(Duration.ofMillis(30))
        .untilAsserted(
            () -> {
              assertThat(reconciler.getEventCount()).isEqualTo(eventCount + 1);
            });

    // second retry
    await()
        .pollDelay(Duration.ofMillis(30))
        .untilAsserted(
            () -> {
              assertThat(reconciler.getEventCount()).isEqualTo(eventCount + 2);
            });

    addNoMoreExceptionAnnotation();

    await()
        .untilAsserted(
            () -> {
              var r = getResource();
              assertThat(r.getMetadata().getFinalizers()).doesNotContain(FINALIZER);
            });

    removeAdditionalFinalizerWaitForResourceDeletion();
  }

  @Test
  void additionalEventAfterExhaustedRetry() {
    var reconciler = extension.getReconcilerOfType(TriggerReconcilerOnAllEventReconciler.class);
    reconciler.setThrowExceptionIfNoAnnotation(true);
    var res = testResource();
    res.addFinalizer(ADDITIONAL_FINALIZER);
    extension.create(res);
    extension.delete(getResource());

    await()
        .pollDelay(Duration.ofMillis(30))
        .untilAsserted(
            () -> {
              assertThat(reconciler.getEventCount()).isEqualTo(MAX_RETRY_ATTEMPTS + 1);
            });

    // this also triggers the reconciliation
    addNoMoreExceptionAnnotation();

    await()
        .pollDelay(Duration.ofMillis(30))
        .untilAsserted(
            () -> {
              assertThat(reconciler.getEventCount()).isGreaterThan(MAX_RETRY_ATTEMPTS + 1);
            });

    removeAdditionalFinalizerWaitForResourceDeletion();
  }

  private void removeAdditionalFinalizerWaitForResourceDeletion() {
    var res = getResource();
    res.removeFinalizer(ADDITIONAL_FINALIZER);
    extension.update(res);
    await().untilAsserted(() -> assertThat(getResource()).isNull());
  }

  private void addNoMoreExceptionAnnotation() {
    TriggerReconcilerOnAllEventCustomResource res;
    res = getResource();
    res.getMetadata().getAnnotations().put(NO_MORE_EXCEPTION_ANNOTATION_KEY, "true");
    extension.update(res);
  }

  TriggerReconcilerOnAllEventCustomResource getResource() {
    return extension.get(TriggerReconcilerOnAllEventCustomResource.class, TEST);
  }

  TriggerReconcilerOnAllEventCustomResource testResource() {
    var res = new TriggerReconcilerOnAllEventCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST).build());
    return res;
  }
}

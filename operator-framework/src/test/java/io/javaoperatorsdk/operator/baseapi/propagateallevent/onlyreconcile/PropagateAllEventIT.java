package io.javaoperatorsdk.operator.baseapi.propagateallevent.onlyreconcile;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;

import static io.javaoperatorsdk.operator.baseapi.propagateallevent.onlyreconcile.PropagateEventReconciler.ADDITIONAL_FINALIZER;
import static io.javaoperatorsdk.operator.baseapi.propagateallevent.onlyreconcile.PropagateEventReconciler.FINALIZER;
import static io.javaoperatorsdk.operator.baseapi.propagateallevent.onlyreconcile.PropagateEventReconciler.NO_MORE_EXCEPTION_ANNOTATION_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class PropagateAllEventIT {

  public static final String TEST = "test1";
  public static final int MAX_RETRY_ATTEMPTS = 2;

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(
              new PropagateEventReconciler(),
              o ->
                  o.withRetry(
                      new GenericRetry()
                          .setInitialInterval(800)
                          .setMaxAttempts(MAX_RETRY_ATTEMPTS)
                          .setIntervalMultiplier(1)))
          .build();

  @Test
  void eventsPresent() {
    var reconciler = extension.getReconcilerOfType(PropagateEventReconciler.class);
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
              assertThat(reconciler.isDeleteEventPresent()).isTrue();
              assertThat(reconciler.isEventOnMarkedForDeletion()).isTrue();
            });
  }

  @Test
  void deleteEventPresentWithoutFinalizer() {
    var reconciler = extension.getReconcilerOfType(PropagateEventReconciler.class);
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
    var reconciler = extension.getReconcilerOfType(PropagateEventReconciler.class);
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

  @Test
  void additionalFinalizer() {
    var reconciler = extension.getReconcilerOfType(PropagateEventReconciler.class);
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

  @Test
  void additionalEventDuringRetryOnDeleteEvent() {

    var reconciler = extension.getReconcilerOfType(PropagateEventReconciler.class);
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

    res = getResource();
    res.getMetadata().getAnnotations().put("my-annotation", "true");
    extension.update(res);
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

    var reconciler = extension.getReconcilerOfType(PropagateEventReconciler.class);
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
    PropagateAllEventCustomResource res;
    res = getResource();
    res.getMetadata().getAnnotations().put(NO_MORE_EXCEPTION_ANNOTATION_KEY, "true");
    extension.update(res);
  }

  PropagateAllEventCustomResource getResource() {
    return extension.get(PropagateAllEventCustomResource.class, TEST);
  }

  PropagateAllEventCustomResource testResource() {
    var res = new PropagateAllEventCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST).build());
    return res;
  }
}

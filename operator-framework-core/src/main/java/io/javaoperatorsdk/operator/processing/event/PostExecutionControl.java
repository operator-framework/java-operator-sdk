package io.javaoperatorsdk.operator.processing.event;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.expectation.Expectation;

final class PostExecutionControl<R extends HasMetadata> {

  private final boolean finalizerRemoved;
  private final R updatedCustomResource;
  private final boolean updateIsStatusPatch;
  private final Exception runtimeException;
  private Expectation<R> expectation;

  private Long reScheduleDelay = null;

  private PostExecutionControl(
      boolean finalizerRemoved,
      R updatedCustomResource,
      boolean updateIsStatusPatch,
      Exception runtimeException) {
    this.finalizerRemoved = finalizerRemoved;
    this.updatedCustomResource = updatedCustomResource;
    this.updateIsStatusPatch = updateIsStatusPatch;
    this.runtimeException = runtimeException;
  }

  public static <R extends HasMetadata> PostExecutionControl<R> onlyFinalizerAdded(
      R updatedCustomResource) {
    return new PostExecutionControl<>(false, updatedCustomResource, false, null);
  }

  public static <R extends HasMetadata> PostExecutionControl<R> defaultDispatch() {
    return new PostExecutionControl<>(false, null, false, null);
  }

  public static <R extends HasMetadata> PostExecutionControl<R> customResourceStatusPatched(
      R updatedCustomResource) {
    return new PostExecutionControl<>(false, updatedCustomResource, true, null);
  }

  public static <R extends HasMetadata> PostExecutionControl<R> customResourcePatched(
      R updatedCustomResource) {
    return new PostExecutionControl<>(false, updatedCustomResource, false, null);
  }

  public static <R extends HasMetadata> PostExecutionControl<R> customResourceFinalizerRemoved(
      R updatedCustomResource) {
    return new PostExecutionControl<>(true, updatedCustomResource, false, null);
  }

  public static <R extends HasMetadata> PostExecutionControl<R> exceptionDuringExecution(
      Exception exception) {
    return new PostExecutionControl<>(false, null, false, exception);
  }

  public Optional<R> getUpdatedCustomResource() {
    return Optional.ofNullable(updatedCustomResource);
  }

  public boolean exceptionDuringExecution() {
    return runtimeException != null;
  }

  public PostExecutionControl<R> withReSchedule(long delay) {
    this.reScheduleDelay = delay;
    return this;
  }

  public PostExecutionControl<R> withExpectation(Expectation<R> expectation) {
    this.expectation = expectation;
    return this;
  }

  public Optional<Exception> getRuntimeException() {
    return Optional.ofNullable(runtimeException);
  }

  public Optional<Long> getReScheduleDelay() {
    return Optional.ofNullable(reScheduleDelay);
  }

  public boolean updateIsStatusPatch() {
    return updateIsStatusPatch;
  }

  @Override
  public String toString() {
    return "PostExecutionControl{"
        + "onlyFinalizerHandled="
        + finalizerRemoved
        + ", updatedCustomResource="
        + updatedCustomResource
        + ", runtimeException="
        + runtimeException
        + '}';
  }

  public boolean isFinalizerRemoved() {
    return finalizerRemoved;
  }

  public Optional<Expectation<R>> getExpectation() {
    return Optional.ofNullable(expectation);
  }
}

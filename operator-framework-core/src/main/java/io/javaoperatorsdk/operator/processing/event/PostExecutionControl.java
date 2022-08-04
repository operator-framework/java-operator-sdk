package io.javaoperatorsdk.operator.processing.event;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;

final class PostExecutionControl<R extends HasMetadata> {

  private final boolean finalizerRemoved;
  private final R updatedCustomResource;
  private final boolean updateWithOptimisticLocking;
  private final Exception runtimeException;

  private Long reScheduleDelay = null;

  private PostExecutionControl(
      boolean finalizerRemoved,
      R updatedCustomResource,
      boolean updateWithOptimisticLocking, Exception runtimeException) {
    this.finalizerRemoved = finalizerRemoved;
    this.updatedCustomResource = updatedCustomResource;
    this.updateWithOptimisticLocking = updateWithOptimisticLocking;
    this.runtimeException = runtimeException;
  }

  public static <R extends HasMetadata> PostExecutionControl<R> onlyFinalizerAdded(
      R updatedCustomResource) {
    return new PostExecutionControl<>(false, updatedCustomResource, true, null);
  }

  public static <R extends HasMetadata> PostExecutionControl<R> defaultDispatch() {
    return new PostExecutionControl<>(false, null, true, null);
  }

  public static <R extends HasMetadata> PostExecutionControl<R> customResourcePatched(
      R updatedCustomResource) {
    return new PostExecutionControl<>(false, updatedCustomResource, false, null);
  }

  public static <R extends HasMetadata> PostExecutionControl<R> customResourceUpdated(
      R updatedCustomResource) {
    return new PostExecutionControl<>(false, updatedCustomResource, true, null);
  }

  public static <R extends HasMetadata> PostExecutionControl<R> customResourceFinalizerRemoved(
      R updatedCustomResource) {
    return new PostExecutionControl<>(true, updatedCustomResource, true, null);
  }

  public static <R extends HasMetadata> PostExecutionControl<R> exceptionDuringExecution(
      Exception exception) {
    return new PostExecutionControl<>(false, null, true, exception);
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

  public Optional<Exception> getRuntimeException() {
    return Optional.ofNullable(runtimeException);
  }

  public Optional<Long> getReScheduleDelay() {
    return Optional.ofNullable(reScheduleDelay);
  }

  public boolean updateWithOptimisticLocking() {
    return updateWithOptimisticLocking;
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
}

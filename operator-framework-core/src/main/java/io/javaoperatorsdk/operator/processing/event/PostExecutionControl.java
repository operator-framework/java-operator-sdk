package io.javaoperatorsdk.operator.processing.event;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;

final class PostExecutionControl<R extends HasMetadata> {

  private final boolean finalizerRemoved;
  private final R updatedCustomResource;
  private final boolean updateIsStatusPatch;
  private final boolean updateIsResourcePatch;
  private final Exception runtimeException;

  private Long reScheduleDelay = null;

  private PostExecutionControl(
      boolean finalizerRemoved,
      R updatedCustomResource,
      boolean updateIsStatusPatch, boolean updateIsResourcePatch, Exception runtimeException) {
    this.finalizerRemoved = finalizerRemoved;
    this.updatedCustomResource = updatedCustomResource;
    this.updateIsStatusPatch = updateIsStatusPatch;
    this.updateIsResourcePatch = updateIsResourcePatch;
    this.runtimeException = runtimeException;
  }

  public static <R extends HasMetadata> PostExecutionControl<R> onlyFinalizerAdded(
      R updatedCustomResource) {
    return new PostExecutionControl<>(false, updatedCustomResource, false, false, null);
  }

  public static <R extends HasMetadata> PostExecutionControl<R> defaultDispatch() {
    return new PostExecutionControl<>(false, null, false, false, null);
  }

  public static <R extends HasMetadata> PostExecutionControl<R> customResourceStatusPatched(
      R updatedCustomResource) {
    return new PostExecutionControl<>(false, updatedCustomResource, true, false, null);
  }

  public static <R extends HasMetadata> PostExecutionControl<R> customResourceUpdated(
      R updatedCustomResource, boolean patched) {
    return new PostExecutionControl<>(false, updatedCustomResource, false, patched, null);
  }

  public static <R extends HasMetadata> PostExecutionControl<R> customResourceFinalizerRemoved(
      R updatedCustomResource) {
    return new PostExecutionControl<>(true, updatedCustomResource, false, false, null);
  }

  public static <R extends HasMetadata> PostExecutionControl<R> exceptionDuringExecution(
      Exception exception) {
    return new PostExecutionControl<>(false, null, false, false, exception);
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
}

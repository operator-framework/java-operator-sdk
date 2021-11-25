package io.javaoperatorsdk.operator.processing.event;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;

public final class PostExecutionControl<R extends HasMetadata> {

  private final boolean onlyFinalizerHandled;
  private final R updatedCustomResource;
  private final RuntimeException runtimeException;

  private Long reScheduleDelay = null;

  private PostExecutionControl(
      boolean onlyFinalizerHandled,
      R updatedCustomResource,
      RuntimeException runtimeException) {
    this.onlyFinalizerHandled = onlyFinalizerHandled;
    this.updatedCustomResource = updatedCustomResource;
    this.runtimeException = runtimeException;
  }

  public static <R extends HasMetadata> PostExecutionControl<R> onlyFinalizerAdded() {
    return new PostExecutionControl<>(true, null, null);
  }

  public static <R extends HasMetadata> PostExecutionControl<R> defaultDispatch() {
    return new PostExecutionControl<>(false, null, null);
  }

  public static <R extends HasMetadata> PostExecutionControl<R> customResourceUpdated(
      R updatedCustomResource) {
    return new PostExecutionControl<>(false, updatedCustomResource, null);
  }

  public static <R extends HasMetadata> PostExecutionControl<R> exceptionDuringExecution(
      RuntimeException exception) {
    return new PostExecutionControl<>(false, null, exception);
  }

  public boolean isOnlyFinalizerHandled() {
    return onlyFinalizerHandled;
  }

  public Optional<R> getUpdatedCustomResource() {
    return Optional.ofNullable(updatedCustomResource);
  }

  public boolean customResourceUpdatedDuringExecution() {
    return updatedCustomResource != null;
  }

  public boolean exceptionDuringExecution() {
    return runtimeException != null;
  }

  public PostExecutionControl<R> withReSchedule(long delay) {
    this.reScheduleDelay = delay;
    return this;
  }

  public Optional<RuntimeException> getRuntimeException() {
    return Optional.ofNullable(runtimeException);
  }

  public Optional<Long> getReScheduleDelay() {
    return Optional.ofNullable(reScheduleDelay);
  }

  @Override
  public String toString() {
    return "PostExecutionControl{"
        + "onlyFinalizerHandled="
        + onlyFinalizerHandled
        + ", updatedCustomResource="
        + updatedCustomResource
        + ", runtimeException="
        + runtimeException
        + '}';
  }
}

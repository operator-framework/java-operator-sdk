package io.javaoperatorsdk.operator.processing;

import java.util.Optional;

import io.fabric8.kubernetes.client.CustomResource;

public final class PostExecutionControl<R extends CustomResource<?, ?>> {

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

  public static PostExecutionControl onlyFinalizerAdded() {
    return new PostExecutionControl(true, null, null);
  }

  public static PostExecutionControl defaultDispatch() {
    return new PostExecutionControl(false, null, null);
  }

  public static <R extends CustomResource<?, ?>> PostExecutionControl<R> customResourceUpdated(
      R updatedCustomResource) {
    return new PostExecutionControl<>(false, updatedCustomResource, null);
  }

  public static PostExecutionControl exceptionDuringExecution(RuntimeException exception) {
    return new PostExecutionControl(false, null, exception);
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

  public PostExecutionControl withReSchedule(long delay) {
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

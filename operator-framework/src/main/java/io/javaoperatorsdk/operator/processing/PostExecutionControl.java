package io.javaoperatorsdk.operator.processing;

import io.fabric8.kubernetes.client.CustomResource;
import java.util.Optional;

public final class PostExecutionControl {

  private final boolean onlyFinalizerHandled;

  private final CustomResource updatedCustomResource;

  private final RuntimeException runtimeException;

  private PostExecutionControl(
      boolean onlyFinalizerHandled,
      CustomResource updatedCustomResource,
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

  public static PostExecutionControl customResourceUpdated(CustomResource updatedCustomResource) {
    return new PostExecutionControl(false, updatedCustomResource, null);
  }

  public static PostExecutionControl exceptionDuringExecution(RuntimeException exception) {
    return new PostExecutionControl(false, null, exception);
  }

  public boolean isOnlyFinalizerHandled() {
    return onlyFinalizerHandled;
  }

  public Optional<CustomResource> getUpdatedCustomResource() {
    return Optional.ofNullable(updatedCustomResource);
  }

  public boolean customResourceUpdatedDuringExecution() {
    return updatedCustomResource != null;
  }

  public boolean exceptionDuringExecution() {
    return runtimeException != null;
  }

  public Optional<RuntimeException> getRuntimeException() {
    return Optional.ofNullable(runtimeException);
  }
}

package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class ErrorStatusUpdateControl<P extends HasMetadata> {

  private final P resource;
  private final boolean patch;
  private boolean noRetry = false;


  public static <T extends HasMetadata> ErrorStatusUpdateControl<T> patchStatus(T resource) {
    return new ErrorStatusUpdateControl<>(resource, true);
  }

  public static <T extends HasMetadata> ErrorStatusUpdateControl<T> updateStatus(T resource) {
    return new ErrorStatusUpdateControl<>(resource, false);
  }

  public static <T extends HasMetadata> ErrorStatusUpdateControl<T> noStatusUpdate() {
    return new ErrorStatusUpdateControl<>(null, true);
  }

  private ErrorStatusUpdateControl(P resource, boolean patch) {
    this.resource = resource;
    this.patch = patch;
  }

  /**
   * Instructs the controller to not retry the error. This is useful for non-recoverable errors.
   *
   * @return ErrorStatusUpdateControl
   */
  public ErrorStatusUpdateControl<P> withNoRetry() {
    this.noRetry = true;
    return this;
  }

  public Optional<P> getResource() {
    return Optional.ofNullable(resource);
  }

  public boolean isNoRetry() {
    return noRetry;
  }

  public boolean isPatch() {
    return patch;
  }
}

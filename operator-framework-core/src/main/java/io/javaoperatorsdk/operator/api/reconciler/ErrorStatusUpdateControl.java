package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class ErrorStatusUpdateControl<P extends HasMetadata> {

  private final P resource;
  private final boolean patch;
  private boolean noRetry = false;
  private final boolean onlyOnChange;

  public static <T extends HasMetadata> ErrorStatusUpdateControl<T> patchStatus(T resource) {
    return new ErrorStatusUpdateControl<>(resource, true, false);
  }

  public static <T extends HasMetadata> ErrorStatusUpdateControl<T> updateStatus(T resource) {
    return new ErrorStatusUpdateControl<>(resource, false, false);
  }

  public static <T extends HasMetadata> ErrorStatusUpdateControl<T> patchStatusIfChanged(
      T resource) {
    return new ErrorStatusUpdateControl<>(resource, true, true);
  }

  public static <T extends HasMetadata> ErrorStatusUpdateControl<T> updateStatusIfChanged(
      T resource) {
    return new ErrorStatusUpdateControl<>(resource, false, true);
  }

  public static <T extends HasMetadata> ErrorStatusUpdateControl<T> noStatusUpdate() {
    return new ErrorStatusUpdateControl<>(null, true, false);
  }

  private ErrorStatusUpdateControl(P resource, boolean patch, boolean onlyOnChange) {
    this.resource = resource;
    this.patch = patch;
    this.onlyOnChange = onlyOnChange;
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

  public boolean isOnlyOnChange() {
    return onlyOnChange;
  }
}

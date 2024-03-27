package io.javaoperatorsdk.operator.api.reconciler;

import java.time.Duration;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class ErrorStatusUpdateControl<P extends HasMetadata>
    extends BaseControl<ErrorStatusUpdateControl<P>> {

  private final P resource;
  private boolean noRetry = false;

  public static <T extends HasMetadata> ErrorStatusUpdateControl<T> patchStatus(T resource) {
    return new ErrorStatusUpdateControl<>(resource);
  }

  public static <T extends HasMetadata> ErrorStatusUpdateControl<T> noStatusUpdate() {
    return new ErrorStatusUpdateControl<>(null);
  }

  private ErrorStatusUpdateControl(P resource) {
    this.resource = resource;
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

  /**
   * If re-scheduled using this method, it is not considered as retry, it effectively cancels retry.
   *
   * @param delay for next execution
   * @return ErrorStatusUpdateControl
   */
  @Override
  public ErrorStatusUpdateControl<P> rescheduleAfter(Duration delay) {
    withNoRetry();
    return super.rescheduleAfter(delay);
  }
}

/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.api.reconciler;

import java.time.Duration;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class ErrorStatusUpdateControl<P extends HasMetadata>
    extends BaseControl<ErrorStatusUpdateControl<P>> {

  private final P resource;
  private boolean noRetry = false;
  private final boolean defaultErrorProcessing;

  public static <T extends HasMetadata> ErrorStatusUpdateControl<T> patchStatus(T resource) {
    return new ErrorStatusUpdateControl<>(resource);
  }

  public static <T extends HasMetadata> ErrorStatusUpdateControl<T> noStatusUpdate() {
    return new ErrorStatusUpdateControl<>(null);
  }

  /**
   * No special processing of the error, the error will be thrown and default error handling will
   * apply
   */
  public static <T extends HasMetadata> ErrorStatusUpdateControl<T> defaultErrorProcessing() {
    return new ErrorStatusUpdateControl<>(null, true);
  }

  private ErrorStatusUpdateControl(P resource) {
    this(resource, false);
  }

  private ErrorStatusUpdateControl(P resource, boolean defaultErrorProcessing) {
    this.resource = resource;
    this.defaultErrorProcessing = defaultErrorProcessing;
  }

  /**
   * Instructs the controller to not retry the error. This is useful for non-recoverable errors.
   *
   * @return ErrorStatusUpdateControl
   */
  public ErrorStatusUpdateControl<P> withNoRetry() {
    if (defaultErrorProcessing) {
      throw new IllegalStateException("Cannot set no-retry for default error processing");
    }
    this.noRetry = true;
    return this;
  }

  public Optional<P> getResource() {
    return Optional.ofNullable(resource);
  }

  public boolean isNoRetry() {
    return noRetry;
  }

  public boolean isDefaultErrorProcessing() {
    return defaultErrorProcessing;
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

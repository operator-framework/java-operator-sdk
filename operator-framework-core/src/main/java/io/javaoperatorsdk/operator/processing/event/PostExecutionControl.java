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
package io.javaoperatorsdk.operator.processing.event;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;

final class PostExecutionControl<R extends HasMetadata> {

  private final boolean finalizerRemoved;
  private final R updatedCustomResource;
  private final boolean updateIsStatusPatch;
  private final Exception runtimeException;

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

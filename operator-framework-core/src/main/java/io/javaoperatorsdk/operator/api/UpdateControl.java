package io.javaoperatorsdk.operator.api;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.client.CustomResource;

public class UpdateControl<T extends CustomResource> {

  private final T customResource;
  private final boolean updateStatusSubResource;
  private final boolean updateCustomResource;
  private Long reScheduleDelay = null;

  private UpdateControl(
      T customResource, boolean updateStatusSubResource, boolean updateCustomResource) {
    if ((updateCustomResource || updateStatusSubResource) && customResource == null) {
      throw new IllegalArgumentException("CustomResource cannot be null in case of update");
    }
    this.customResource = customResource;
    this.updateStatusSubResource = updateStatusSubResource;
    this.updateCustomResource = updateCustomResource;
  }

  public static <T extends CustomResource> UpdateControl<T> updateCustomResource(T customResource) {
    return new UpdateControl<>(customResource, false, true);
  }

  public static <T extends CustomResource> UpdateControl<T> updateStatusSubResource(
      T customResource) {
    return new UpdateControl<>(customResource, true, false);
  }

  /**
   * As a results of this there will be two call to K8S API. First the custom resource will be
   * updates then the status sub-resource.
   * 
   * @param customResource - custom resource to use in both API calls
   * @return UpdateControl instance
   */
  public static <T extends CustomResource> UpdateControl<T> updateCustomResourceAndStatus(
      T customResource) {
    return new UpdateControl<>(customResource, true, true);
  }

  public static <T extends CustomResource> UpdateControl<T> noUpdate() {
    return new UpdateControl<>(null, false, false);
  }

  public UpdateControl withReSchedule(long delay, TimeUnit timeUnit) {
    return withReSchedule(timeUnit.toMillis(delay));
  }

  public UpdateControl withReSchedule(long delay) {
    this.reScheduleDelay = delay;
    return this;
  }

  public Optional<Long> getReScheduleDelay() {
    return Optional.ofNullable(reScheduleDelay);
  }

  public T getCustomResource() {
    return customResource;
  }

  public boolean isUpdateStatusSubResource() {
    return updateStatusSubResource;
  }

  public boolean isUpdateCustomResource() {
    return updateCustomResource;
  }

  public boolean isUpdateCustomResourceAndStatusSubResource() {
    return updateCustomResource && updateStatusSubResource;
  }
}

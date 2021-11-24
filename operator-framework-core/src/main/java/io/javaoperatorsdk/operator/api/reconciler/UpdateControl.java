package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;

@SuppressWarnings("rawtypes")
public class UpdateControl<T extends HasMetadata> extends BaseControl<UpdateControl<T>> {

  private final T resource;
  private final boolean updateStatus;
  private final boolean updateResource;

  private UpdateControl(
      T resource, boolean updateStatus, boolean updateResource) {
    if ((updateResource || updateStatus) && resource == null) {
      throw new IllegalArgumentException("CustomResource cannot be null in case of update");
    }
    this.resource = resource;
    this.updateStatus = updateStatus;
    this.updateResource = updateResource;
  }

  public static <T extends HasMetadata> UpdateControl<T> updateResource(T customResource) {
    return new UpdateControl<>(customResource, false, true);
  }

  public static <T extends HasMetadata> UpdateControl<T> updateStatus(
      T customResource) {
    return new UpdateControl<>(customResource, true, false);
  }

  /**
   * As a results of this there will be two call to K8S API. First the custom resource will be
   * updates then the status sub-resource.
   * @param <T> resource type
   * @param customResource - custom resource to use in both API calls
   * @return UpdateControl instance
   */
  public static <T extends HasMetadata> UpdateControl<T> updateResourceAndStatus(
      T customResource) {
    return new UpdateControl<>(customResource, true, true);
  }

  public static <T extends HasMetadata> UpdateControl<T> noUpdate() {
    return new UpdateControl<>(null, false, false);
  }

  public T getResource() {
    return resource;
  }

  public boolean isUpdateStatus() {
    return updateStatus;
  }

  public boolean isUpdateResource() {
    return updateResource;
  }

  public boolean isNoUpdate() {
    return !updateResource && !updateStatus;
  }

  public boolean isUpdateResourceAndStatus() {
    return updateResource && updateStatus;
  }
}

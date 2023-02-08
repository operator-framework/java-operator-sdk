package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.CustomResource;

public class UpdateControl<P extends HasMetadata> extends BaseControl<UpdateControl<P>> {

  private final P resource;
  private final boolean updateStatus;
  private final boolean updateResource;
  private final boolean patchStatus;

  private UpdateControl(
      P resource, boolean updateStatus, boolean updateResource, boolean patchStatus) {
    if ((updateResource || updateStatus) && resource == null) {
      throw new IllegalArgumentException("CustomResource cannot be null in case of update");
    }
    this.resource = resource;
    this.updateStatus = updateStatus;
    this.updateResource = updateResource;
    this.patchStatus = patchStatus;
  }

  /**
   * Creates an update control instance that instructs the framework to do an update on resource
   * itself, not on the status. Note that usually as a results of a reconciliation should be a
   * status update not an update to the resource itself.
   *
   * Using this update makes sure that the resource in the next reconciliation is the updated one -
   * this is not guaranteed by default if you do an update on a resource by the Kubernetes client.
   *
   * @param <T> custom resource type
   * @param customResource customResource to use for update
   * @return initialized update control
   */
  public static <T extends HasMetadata> UpdateControl<T> updateResource(T customResource) {
    return new UpdateControl<>(customResource, false, true, false);
  }

  /**
   * Preferred way to update the status. It does not do optimistic locking. Uses JSON Patch to patch
   * the resource.
   * <p>
   * Note that this does not work, if the {@link CustomResource#initStatus() initStatus} is
   * implemented, since it breaks the diffing process. Don't implement it if using this method.
   * </p>
   * There is also an issue with setting value to null with older Kubernetes versions (1.19 and
   * below). See: <a href=
   * "https://github.com/fabric8io/kubernetes-client/issues/4158">https://github.com/fabric8io/kubernetes-client/issues/4158</a>
   *
   * @param <T> resource type
   * @param customResource the custom resource with target status
   * @return UpdateControl instance
   */
  public static <T extends HasMetadata> UpdateControl<T> patchStatus(T customResource) {
    return new UpdateControl<>(customResource, true, false, true);
  }

  /**
   * Note that usually "patchStatus" is advised to be used instead of this method.
   * <p>
   * Updates the status with optimistic locking regarding current resource version reconciled. Note
   * that this also ensures that on next reconciliation is the most up-to-date custom resource is
   * used.
   * </p>
   *
   * @param <T> resource type
   * @param customResource the custom resource with target status
   * @return UpdateControl instance
   */
  public static <T extends HasMetadata> UpdateControl<T> updateStatus(T customResource) {
    return new UpdateControl<>(customResource, true, false, false);
  }

  /**
   * As a results of this there will be two call to K8S API. First the custom resource will be
   * updates then the status sub-resource.
   *
   * Using this update makes sure that the resource in the next reconciliation is the updated one -
   * this is not guaranteed by default if you do an update on a resource by the Kubernetes client.
   *
   * @param <T> resource type
   * @param customResource - custom resource to use in both API calls
   * @return UpdateControl instance
   */
  public static <T extends HasMetadata> UpdateControl<T> updateResourceAndStatus(
      T customResource) {
    return new UpdateControl<>(customResource, true, true, false);
  }

  /**
   * Updates the resource - with optimistic locking - and patches the status without optimistic
   * locking in place.
   *
   * Note that using this method, it is not guaranteed that the most recent updated resource will be
   * in case for next reconciliation.
   *
   * @param customResource to update
   * @return UpdateControl instance
   * @param <T> resource type
   */
  public static <T extends HasMetadata> UpdateControl<T> updateResourceAndPatchStatus(
      T customResource) {
    return new UpdateControl<>(customResource, true, true, true);
  }

  /**
   * Marked for removal, because of confusing name. It does not patch the resource but rather
   * updates it.
   *
   * @deprecated use {@link UpdateControl#updateResourceAndPatchStatus(HasMetadata)}
   *
   * @param customResource to update
   * @return UpdateControl instance
   * @param <T> resource type
   */
  @Deprecated(forRemoval = true)
  public static <T extends HasMetadata> UpdateControl<T> patchResourceAndStatus(T customResource) {
    return updateResourceAndStatus(customResource);
  }

  public static <T extends HasMetadata> UpdateControl<T> noUpdate() {
    return new UpdateControl<>(null, false, false, false);
  }

  public P getResource() {
    return resource;
  }

  public boolean isUpdateStatus() {
    return updateStatus;
  }

  public boolean isUpdateResource() {
    return updateResource;
  }

  public boolean isPatchStatus() {
    return patchStatus;
  }

  public boolean isNoUpdate() {
    return !updateResource && !updateStatus;
  }

  public boolean isUpdateResourceAndStatus() {
    return updateResource && updateStatus;
  }

}

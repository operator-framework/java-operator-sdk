package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;

public class UpdateControl<P extends HasMetadata> extends BaseControl<UpdateControl<P>> {

  private final P resource;
  private final boolean updateStatus;
  private final boolean updateResource;
  private final boolean patch;
  private final boolean onlyOnChange;
  private final PatchContext patchContext;

  private UpdateControl(P resource, boolean updateStatus, boolean updateResource, boolean patch,boolean onlyOnChange) {
    this(resource,updateStatus,updateResource,patch,onlyOnChange,null);
  }

  private UpdateControl(
          P resource, boolean updateStatus, boolean updateResource, boolean patch, boolean onlyOnChange, PatchContext patchContext) {

    if ((updateResource || updateStatus) && resource == null) {
      throw new IllegalArgumentException("CustomResource cannot be null in case of update");
    }
    this.resource = resource;
    this.updateStatus = updateStatus;
    this.updateResource = updateResource;
    this.patch = patch;
    this.onlyOnChange = onlyOnChange;
    this.patchContext = patchContext;
    
  }

  /**
   * Creates an update control instance that instructs the framework to do an update on resource
   * itself, not on the status. Note that usually as a results of a reconciliation should be a
   * status update not an update to the resource itself.
   *
   * @param <T> custom resource type
   * @param customResource customResource to use for update
   * @return initialized update control
   */
  public static <T extends HasMetadata> UpdateControl<T> updateResource(T customResource) {
    return new UpdateControl<>(customResource, false, true, false, false);
  }

  public static <T extends HasMetadata> UpdateControl<T> updateResourceIfChanged(T customResource) {
    return new UpdateControl<>(customResource, false, true, false, true);
  }


  public static <T extends HasMetadata> UpdateControl<T> patchResource(T customResource, PatchContext patchContext) {
    return new UpdateControl<>(customResource, false, true, true, patchContext);
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
   * @param customResource the resource with target status
   * @return UpdateControl instance
   */
  public static <T extends HasMetadata> UpdateControl<T> patchStatus(T customResource) {
    return new UpdateControl<>(customResource, true, false, true, false);
  }

  /**
   * Patches status only if it not equals to original status. It uses equals method to compare the
   * two. Only for custom resources.
   *
   * @param customResource the resource with target status
   * @param <T> resource type
   * @return UpdateControl instance
   */
  public static <T extends HasMetadata> UpdateControl<T> patchStatusIfChanged(T customResource) {
    return new UpdateControl<>(customResource, true, false, true, true);
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
    return new UpdateControl<>(customResource, true, false, false, false);
  }

  /**
   * Updates status only if it not equals to original status. It uses equals method to compare the
   * two. Only for custom resources.
   *
   * @param <T> resource type
   * @param customResource the custom resource with target status
   * @return UpdateControl instance
   */
  public static <T extends HasMetadata> UpdateControl<T> updateStatusIfChanged(T customResource) {
    return new UpdateControl<>(customResource, true, false, false, true);
  }

  /**
   * As a results of this there will be two call to K8S API. First the custom resource will be
   * updates then the status sub-resource.
   *
   * @param <T> resource type
   * @param customResource - custom resource to use in both API calls
   * @return UpdateControl instance
   */
  public static <T extends HasMetadata> UpdateControl<T> updateResourceAndStatus(
      T customResource) {
    return new UpdateControl<>(customResource, true, true, false, false);
  }

  /**
   * Same as updateResourceAndStatus, only does the update requests if resource are changed.
   * Resources are compared using equals method. Only for custom resources.
   *
   * @param <T> resource type
   * @param customResource - custom resource to use in both API calls
   * @return UpdateControl instance
   */
  public static <T extends HasMetadata> UpdateControl<T> updateResourceAndStatusIfChanged(
      T customResource) {
    return new UpdateControl<>(customResource, true, true, false, true);
  }

  /**
   * As a results of this there will be two call to K8S API. First the custom resource will be
   * updated then the status sub-resource patched. The patch does not use optimistic locking.
   *
   * @param <T> resource type
   * @param customResource - custom resource to use in both API calls
   * @return UpdateControl instance
   */
  @Deprecated(forRemoval = true)
  public static <T extends HasMetadata> UpdateControl<T> patchResourceAndStatus(
      T customResource) {
    return new UpdateControl<>(customResource, true, true, true, false);
  }

  /**
   * As a results of this there will be two call to K8S API. First the custom resource will be
   * patched then the status sub-resource. The patch does not use optimistic locking.
   *
   * @param <T> resource type
   * @param customResource - custom resource to use in both API calls
   * @return UpdateControl instance
   */
  public static <T extends HasMetadata> UpdateControl<T> updateResourceAndPatchStatus(
      T customResource) {
    return new UpdateControl<>(customResource, true, true, true, false);
  }

  /**
   * Same as patchResourceAndStatus, only does the update requests if resource are changed.
   * Resources are compared using equals method. Only for custom resources.
   *
   * @param <T> resource type
   * @param customResource - custom resource to use in both API calls
   * @return UpdateControl instance
   */
  public static <T extends HasMetadata> UpdateControl<T> updateResourceAndPatchStatusIfChanged(
      T customResource) {
    return new UpdateControl<>(customResource, true, true, true, true);
  }

  public static <T extends HasMetadata> UpdateControl<T> noUpdate() {
    return new UpdateControl<>(null, false, false, false, false);
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

  public boolean isPatch() {
    return patch;
  }

  public boolean isNoUpdate() {
    return !updateResource && !updateStatus;
  }

  public boolean isUpdateResourceAndStatus() {
    return updateResource && updateStatus;
  }

  public PatchContext getPatchContext() {
    return patchContext;
  }

  public boolean onlyOnChange() {
    return onlyOnChange;
  }
}

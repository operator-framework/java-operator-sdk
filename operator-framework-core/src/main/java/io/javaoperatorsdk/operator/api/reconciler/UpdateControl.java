package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.CustomResource;

@SuppressWarnings("rawtypes")
public class UpdateControl<T extends HasMetadata> extends BaseControl<UpdateControl<T>> {

  private final T customResource;
  private final boolean updateStatusSubResource;
  private final boolean updateCustomResource;

  private UpdateControl(
      T customResource, boolean updateStatusSubResource, boolean updateCustomResource) {
    if ((updateCustomResource || updateStatusSubResource) && customResource == null) {
      throw new IllegalArgumentException("CustomResource cannot be null in case of update");
    }
    this.customResource = customResource;
    this.updateStatusSubResource = updateStatusSubResource;
    this.updateCustomResource = updateCustomResource;
  }

  public static <T extends HasMetadata> UpdateControl<T> updateCustomResource(T customResource) {
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
  public static <T extends HasMetadata> UpdateControl<T> updateCustomResourceAndStatus(
      T customResource) {
    return new UpdateControl<>(customResource, true, true);
  }

  public static <T extends HasMetadata> UpdateControl<T> noUpdate() {
    return new UpdateControl<>(null, false, false);
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

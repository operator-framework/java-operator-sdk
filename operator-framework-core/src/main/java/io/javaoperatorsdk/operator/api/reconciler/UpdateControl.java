package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.CustomResource;

public class UpdateControl<P extends HasMetadata> extends BaseControl<UpdateControl<P>, P> {

  private final P resource;
  private final boolean patchResource;
  private final boolean patchStatus;

  private UpdateControl(P resource, boolean patchResource, boolean patchStatus) {
    if ((patchResource || patchStatus) && resource == null) {
      throw new IllegalArgumentException("CustomResource cannot be null in case of update");
    }
    this.resource = resource;
    this.patchResource = patchResource;
    this.patchStatus = patchStatus;
  }

  /**
   * Preferred way to update the status. It does not do optimistic locking. Uses JSON Patch to patch
   * the resource.
   *
   * <p>Note that this does not work, if the {@link CustomResource#initStatus()} is implemented,
   * since it breaks the diffing process. Don't implement it if using this method. There is also an
   * issue with setting value to {@code null} with older Kubernetes versions (1.19 and below). See:
   * <a href=
   * "https://github.com/fabric8io/kubernetes-client/issues/4158">https://github.com/fabric8io/kubernetes-client/issues/4158</a>
   *
   * @param <T> resource type
   * @param customResource the custom resource with target status
   * @return UpdateControl instance
   */
  public static <T extends HasMetadata> UpdateControl<T> patchStatus(T customResource) {
    return new UpdateControl<>(customResource, false, true);
  }

  public static <T extends HasMetadata> UpdateControl<T> patchResource(T customResource) {
    return new UpdateControl<>(customResource, true, false);
  }

  /**
   * @param customResource to update
   * @return UpdateControl instance
   * @param <T> resource type
   */
  public static <T extends HasMetadata> UpdateControl<T> patchResourceAndStatus(T customResource) {
    return new UpdateControl<>(customResource, true, true);
  }

  public static <T extends HasMetadata> UpdateControl<T> noUpdate() {
    return new UpdateControl<>(null, false, false);
  }

  public Optional<P> getResource() {
    return Optional.ofNullable(resource);
  }

  public boolean isPatchResource() {
    return patchResource;
  }

  public boolean isPatchStatus() {
    return patchStatus;
  }

  public boolean isNoUpdate() {
    return !patchResource && !patchStatus;
  }

  public boolean isPatchResourceAndStatus() {
    return patchResource && patchStatus;
  }
}

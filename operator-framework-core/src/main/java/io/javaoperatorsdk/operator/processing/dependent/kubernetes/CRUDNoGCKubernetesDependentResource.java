package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;

public class CRUDNoGCKubernetesDependentResource<R extends HasMetadata, P extends HasMetadata>
    extends
    KubernetesDependentResource<R, P>
    implements Creator<R, P>, Updater<R, P>, Deleter<P> {

  public CRUDNoGCKubernetesDependentResource(Class<R> resourceType) {
    super(resourceType);
  }
}

package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReadOnlyDependentResource;

public class KubernetesReadOnlyResource<R extends HasMetadata, P extends HasMetadata>
    extends KubernetesDependentResourceBase<R, P, InformerConfig>
    implements ReadOnlyDependentResource<R, P> {
  @Override
  public Optional<R> getResource(P primaryResource) {
    return informerEventSource.getAssociated(primaryResource);
  }
}

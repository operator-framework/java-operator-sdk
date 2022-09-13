package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public interface BulkDependentResource<R, P extends HasMetadata> {

  int count(P primary, Context<P> context);

  default R desired(P primary, int index, Context<P> context) {
    throw new IllegalStateException("Implement if the dependent resource is a creator or updater");
  }

  void deleteBulkResourceWithIndex(P primary, R resource, int i, Context<P> context);

  BulkResourceDiscriminatorFactory<R, P> bulkResourceDiscriminatorFactory();

}

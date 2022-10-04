package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;

public interface BulkResourceDiscriminatorFactory<R, P extends HasMetadata> {

  ResourceDiscriminator<R, P> createResourceDiscriminator(int index);

}

package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;

/**
 * @param <R> resource type
 */
public class OwnerReferenceSecondaryToPrimaryMapper<R extends HasMetadata>
    implements SecondaryToPrimaryMapper<R> {
  @Override
  public Set<ResourceID> toPrimaryResourceIDs(R secondaryResource) {
    return Mappers.fromOwnerReference().toPrimaryResourceIDs(secondaryResource);
  }
}

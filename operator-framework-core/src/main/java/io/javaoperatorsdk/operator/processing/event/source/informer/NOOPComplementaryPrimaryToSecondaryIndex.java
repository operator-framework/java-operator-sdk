package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class NOOPComplementaryPrimaryToSecondaryIndex<R extends HasMetadata>
    implements ComplementaryPrimaryToSecondaryIndex<R> {

  @Override
  public void explicitAdd(R resource) {}

  @Override
  public void cleanupForResource(R resourceID) {}

  @Override
  public Set<ResourceID> getComplementarySecondaryResources(ResourceID primary) {
    return Set.of();
  }
}

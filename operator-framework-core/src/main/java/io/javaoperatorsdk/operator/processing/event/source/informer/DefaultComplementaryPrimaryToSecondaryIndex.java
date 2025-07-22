package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class DefaultComplementaryPrimaryToSecondaryIndex<R extends HasMetadata>
    implements ComplementaryPrimaryToSecondaryIndex<R> {

  private final ConcurrentHashMap<ResourceID, Set<ResourceID>> index = new ConcurrentHashMap<>();

  @Override
  public void explicitAddOrUpdate(R resource) {}

  @Override
  public void onCreateOrUpdateEvent(R resourceID) {}

  @Override
  public Set<ResourceID> getComplementarySecondaryResources(ResourceID primary) {
    return Set.of();
  }
}

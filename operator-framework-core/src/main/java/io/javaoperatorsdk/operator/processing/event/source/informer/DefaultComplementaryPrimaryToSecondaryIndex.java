package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;

public class DefaultComplementaryPrimaryToSecondaryIndex<R extends HasMetadata>
    implements ComplementaryPrimaryToSecondaryIndex<R> {

  private final ConcurrentHashMap<ResourceID, Set<ResourceID>> index = new ConcurrentHashMap<>();
  private final SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper;

  public DefaultComplementaryPrimaryToSecondaryIndex(
      SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper) {
    this.secondaryToPrimaryMapper = secondaryToPrimaryMapper;
  }

  @Override
  public void explicitAdd(R resource) {
    Set<ResourceID> primaryResources = secondaryToPrimaryMapper.toPrimaryResourceIDs(resource);
    primaryResources.forEach(
        primaryResource -> {
          var resourceSet =
              index.computeIfAbsent(primaryResource, pr -> ConcurrentHashMap.newKeySet());
          resourceSet.add(ResourceID.fromResource(resource));
        });
  }

  @Override
  public void cleanupForResource(R resource) {
    Set<ResourceID> primaryResources = secondaryToPrimaryMapper.toPrimaryResourceIDs(resource);
    primaryResources.forEach(
        primaryResource -> {
          var secondaryResources = index.get(primaryResource);
          // this can be null in just very special cases, like when the secondaryToPrimaryMapper is
          // changing dynamically. Like if a list of ResourceIDs mapped dynamically extended in the
          // mapper between the onAddOrUpdate and onDelete is called.
          if (secondaryResources != null) {
            secondaryResources.remove(ResourceID.fromResource(resource));
            if (secondaryResources.isEmpty()) {
              index.remove(primaryResource);
            }
          }
        });
  }

  @Override
  public Set<ResourceID> getSecondaryResources(ResourceID primary) {
    var resourceIDs = index.get(primary);
    if (resourceIDs == null) {
      return Collections.emptySet();
    } else {
      return new HashSet<>(resourceIDs);
    }
  }
}

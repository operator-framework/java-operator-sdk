package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.*;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;

// todo unit test
public class PrimaryToSecondaryIndex<R extends HasMetadata, P extends HasMetadata> {

  private SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper;
  private Map<ResourceID, Set<ResourceID>> index = new HashMap<>();

  public PrimaryToSecondaryIndex(SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper) {
    this.secondaryToPrimaryMapper = secondaryToPrimaryMapper;
  }

  public synchronized void onAddOrUpdate(R resource) {
    Set<ResourceID> primaryResources = secondaryToPrimaryMapper.toPrimaryResourceIDs(resource);
    primaryResources.forEach(
        primaryResource -> {
          var resourceSet = index.computeIfAbsent(primaryResource, pr -> new HashSet<>());
          resourceSet.add(ResourceID.fromResource(resource));
        });
  }

  public synchronized void onDelete(R resource) {
    Set<ResourceID> primaryResources = secondaryToPrimaryMapper.toPrimaryResourceIDs(resource);
    primaryResources.forEach(
        primaryResource -> {
          var secondaryResources = index.get(primaryResource);
          secondaryResources.remove(ResourceID.fromResource(resource));
          if (secondaryResources.isEmpty()) {
            index.remove(primaryResource);
          }
        });
  }

  public synchronized Set<ResourceID> getSecondaryResources(P primary) {
    var resourceIDs = index.get(ResourceID.fromResource(primary));
    return Collections.unmodifiableSet(resourceIDs);
  }
}

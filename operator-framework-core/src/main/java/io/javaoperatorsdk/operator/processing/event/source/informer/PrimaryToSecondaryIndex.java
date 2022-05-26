package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.*;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;

class PrimaryToSecondaryIndex<R extends HasMetadata> {

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

  public synchronized Set<ResourceID> getSecondaryResources(ResourceID primary) {
    var resourceIDs = index.get(primary);
    if (resourceIDs == null) {
      return Collections.emptySet();
    } else {
      // see https://github.com/java-operator-sdk/java-operator-sdk/issues/1242
      return Set.copyOf(resourceIDs);
    }
  }
}

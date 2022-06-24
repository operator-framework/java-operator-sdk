package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;

class DefaultPrimaryToSecondaryIndex<R extends HasMetadata> implements PrimaryToSecondaryIndex<R> {

  private SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper;
  private Map<ResourceID, Set<ResourceID>> index = new HashMap<>();

  public DefaultPrimaryToSecondaryIndex(SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper) {
    this.secondaryToPrimaryMapper = secondaryToPrimaryMapper;
  }

  @Override
  public synchronized void onAddOrUpdate(R resource) {
    Set<ResourceID> primaryResources = secondaryToPrimaryMapper.toPrimaryResourceIDs(resource);
    primaryResources.forEach(
        primaryResource -> {
          var resourceSet =
              index.computeIfAbsent(primaryResource, pr -> ConcurrentHashMap.newKeySet());
          resourceSet.add(ResourceID.fromResource(resource));
        });
  }

  @Override
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

  @Override
  public synchronized Set<ResourceID> getSecondaryResources(ResourceID primary) {
    var resourceIDs = index.get(primary);
    if (resourceIDs == null) {
      return Collections.emptySet();
    } else {
      return Collections.unmodifiableSet(resourceIDs);
    }
  }
}
